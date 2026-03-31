//go:build windows

package capture

import (
	"context"
	"fmt"
	"io"
	"os/exec"
	"runtime"
	"strconv"
	"time"
	"unsafe"

	"github.com/go-ole/go-ole"
	"github.com/moutend/go-wca/pkg/wca"
)

// We always feed ffmpeg the same PCM format. WASAPI's AUTOCONVERTPCM flag
// in shared mode converts whatever the device's mix format is (typically
// float32 48k stereo) to this for us, so we never have to deal with
// resampling or float-to-int conversion in Go.
const (
	pcmSampleRate    = 48000
	pcmChannels      = 2
	pcmBitsPerSample = 16
	pcmBlockAlign    = (pcmBitsPerSample / 8) * pcmChannels
)

// StartAudio captures Windows system audio via WASAPI shared-mode loopback
// on the default render endpoint, pipes the PCM into ffmpeg for Opus
// encoding, and feeds the resulting ogg/opus stream onto an audio track.
//
// ffmpeg is still required (just as an Opus encoder, not for capture).
// Returns ErrAudioUnavailable if ffmpeg is missing so the session can
// continue without audio.
func StartAudio(ctx context.Context, t Target) error {
	if t.AddAudioTrack == nil {
		return ErrAudioUnavailable
	}
	if _, err := exec.LookPath("ffmpeg"); err != nil {
		t.Logger.Println("audio: ffmpeg not found, skipping audio capture")
		return ErrAudioUnavailable
	}

	track, err := newOpusTrack()
	if err != nil {
		return fmt.Errorf("creating opus audio track: %w", err)
	}
	if err := t.AddAudioTrack(track); err != nil {
		return fmt.Errorf("adding audio track: %w", err)
	}

	cmd := exec.CommandContext(ctx, "ffmpeg",
		"-hide_banner",
		"-loglevel", "warning",
		"-f", "s16le",
		"-ar", strconv.Itoa(pcmSampleRate),
		"-ac", strconv.Itoa(pcmChannels),
		"-i", "pipe:0",
		"-c:a", "libopus",
		"-b:a", "96k",
		"-application", "lowdelay",
		"-frame_duration", "20",
		"-vbr", "on",
		"-f", "ogg",
		"pipe:1",
	)

	if t.LogFile != nil {
		cmd.Stderr = t.LogFile
	} else {
		cmd.Stderr = io.Discard
	}

	stdin, err := cmd.StdinPipe()
	if err != nil {
		return fmt.Errorf("audio stdin pipe: %w", err)
	}
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		_ = stdin.Close()
		return fmt.Errorf("audio stdout pipe: %w", err)
	}
	if err := cmd.Start(); err != nil {
		_ = stdin.Close()
		_ = stdout.Close()
		return fmt.Errorf("starting audio ffmpeg: %w", err)
	}

	t.Logger.Printf("audio capture started (wasapi loopback, ffmpeg pid=%d)", cmd.Process.Pid)

	go func() {
		err := captureWASAPILoopback(ctx, stdin, t.Logger.Printf)
		_ = stdin.Close()
		if err != nil && ctx.Err() == nil {
			t.Logger.Printf("audio: wasapi capture ended: %v", err)
		}
	}()

	go func() {
		defer func() {
			if cmd.Process != nil {
				_ = cmd.Process.Kill()
			}
			_ = stdout.Close()
			_ = cmd.Wait()
			t.Logger.Println("audio capture stopped")
		}()
		_ = pumpOggOpus(ctx, stdout, track, t.Logger.Printf)
	}()

	return nil
}

// captureWASAPILoopback opens the default render endpoint in loopback mode
// and writes 48kHz s16le stereo PCM to w until ctx is cancelled. WASAPI
// gives us no event when nothing is playing, so we poll on a short period;
// when the device is silent GetBuffer just returns zero frames and we sleep
// again. The loopback stream is shared-mode only (per MSDN).
func captureWASAPILoopback(ctx context.Context, w io.Writer, logf func(string, ...any)) (retErr error) {
	// COM apartments are per-thread. Pin this goroutine so the COM objects
	// we Activate stay on the same thread for their whole lifetime.
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	if err := ole.CoInitializeEx(0, ole.COINIT_MULTITHREADED); err != nil {
		// RPC_E_CHANGED_MODE (0x80010106) and S_FALSE (1) just mean COM was
		// already initialised on this thread — that's fine, keep going and
		// skip the matching CoUninitialize.
		if oe, ok := err.(*ole.OleError); !ok || (oe.Code() != 1 && oe.Code() != 0x80010106) {
			return fmt.Errorf("CoInitializeEx: %w", err)
		}
	} else {
		defer ole.CoUninitialize()
	}

	var enumerator *wca.IMMDeviceEnumerator
	if err := wca.CoCreateInstance(
		wca.CLSID_MMDeviceEnumerator, 0, wca.CLSCTX_ALL,
		wca.IID_IMMDeviceEnumerator, &enumerator,
	); err != nil {
		return fmt.Errorf("CoCreateInstance(MMDeviceEnumerator): %w", err)
	}
	defer enumerator.Release()

	var endpoint *wca.IMMDevice
	if err := enumerator.GetDefaultAudioEndpoint(wca.ERender, wca.EConsole, &endpoint); err != nil {
		return fmt.Errorf("GetDefaultAudioEndpoint: %w", err)
	}
	defer endpoint.Release()

	var client *wca.IAudioClient
	if err := endpoint.Activate(wca.IID_IAudioClient, wca.CLSCTX_ALL, nil, &client); err != nil {
		return fmt.Errorf("Activate IAudioClient: %w", err)
	}
	defer client.Release()

	wfx := &wca.WAVEFORMATEX{
		WFormatTag:      1, // WAVE_FORMAT_PCM
		NChannels:       pcmChannels,
		NSamplesPerSec:  pcmSampleRate,
		WBitsPerSample:  pcmBitsPerSample,
		NBlockAlign:     pcmBlockAlign,
		NAvgBytesPerSec: pcmSampleRate * pcmBlockAlign,
		CbSize:          0,
	}

	// 200ms ring buffer — comfortable headroom over the 10ms poll period.
	const bufferDurationHNS wca.REFERENCE_TIME = 2_000_000

	if err := client.Initialize(
		wca.AUDCLNT_SHAREMODE_SHARED,
		wca.AUDCLNT_STREAMFLAGS_LOOPBACK|
			wca.AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM|
			wca.AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY,
		bufferDurationHNS, 0, wfx, nil,
	); err != nil {
		return fmt.Errorf("IAudioClient.Initialize: %w", err)
	}

	var captureClient *wca.IAudioCaptureClient
	if err := client.GetService(wca.IID_IAudioCaptureClient, &captureClient); err != nil {
		return fmt.Errorf("GetService IAudioCaptureClient: %w", err)
	}
	defer captureClient.Release()

	if err := client.Start(); err != nil {
		return fmt.Errorf("IAudioClient.Start: %w", err)
	}
	defer client.Stop()

	// Reusable silence buffer for SILENT-flagged packets. WASAPI hands us
	// uninitialised memory in that case; we have to emit zeros ourselves so
	// the Opus encoder gets a steady stream.
	silenceCache := make([]byte, 4096*pcmBlockAlign)

	const pollPeriod = 10 * time.Millisecond
	firstPacket := false

	for {
		if err := ctx.Err(); err != nil {
			return nil
		}

		var data *byte
		var frames uint32
		var flags uint32
		var devicePos, qpcPos uint64

		if err := captureClient.GetBuffer(&data, &frames, &flags, &devicePos, &qpcPos); err != nil {
			// AUDCLNT_S_BUFFER_EMPTY (0x08890001) is "no data yet, try later".
			time.Sleep(pollPeriod)
			continue
		}
		if frames == 0 {
			_ = captureClient.ReleaseBuffer(frames)
			time.Sleep(pollPeriod)
			continue
		}

		size := int(frames) * pcmBlockAlign
		var chunk []byte
		if flags&wca.AUDCLNT_BUFFERFLAGS_SILENT != 0 {
			if size > len(silenceCache) {
				silenceCache = make([]byte, size)
			}
			chunk = silenceCache[:size]
		} else {
			chunk = unsafe.Slice(data, size)
		}

		_, writeErr := w.Write(chunk)
		_ = captureClient.ReleaseBuffer(frames)
		if writeErr != nil {
			return fmt.Errorf("writing pcm to ffmpeg: %w", writeErr)
		}

		if !firstPacket {
			firstPacket = true
			logf("audio: first wasapi buffer (%d frames)", frames)
		}
	}
}
