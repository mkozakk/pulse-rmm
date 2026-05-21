//go:build linux || windows

package capture

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os/exec"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
	"github.com/pion/webrtc/v4/pkg/media/oggreader"
)

// ErrAudioUnavailable indicates the host has no usable audio capture source.
// Callers should log and proceed without audio rather than failing the session.
var ErrAudioUnavailable = errors.New("audio capture unavailable")

// audioFrameDuration is the Opus frame size we ask ffmpeg to emit.
// Matches `-frame_duration 20` below.
const audioFrameDuration = 20 * time.Millisecond

// newOpusTrack creates a TrackLocalStaticSample for the Opus codec
// registered in session.go (payload type 111, 48kHz stereo).
func newOpusTrack() (*webrtc.TrackLocalStaticSample, error) {
	return webrtc.NewTrackLocalStaticSample(webrtc.RTPCodecCapability{
		MimeType:  webrtc.MimeTypeOpus,
		ClockRate: 48000,
		Channels:  2,
	}, "audio", "desktop")
}

// pumpOggOpus reads an Ogg-wrapped Opus stream from r and writes each Opus
// packet to track as a media.Sample with a fixed 20ms duration.
//
// We feed ffmpeg `-c:a libopus -frame_duration 20 -f ogg pipe:1`. The output
// is a standard Ogg/Opus stream: the first two pages are OpusHead + OpusTags
// (handled by oggreader.NewWith), then each subsequent page payload is one
// Opus packet.
func pumpOggOpus(ctx context.Context, r io.Reader, track *webrtc.TrackLocalStaticSample, log logf) error {
	rd, _, err := oggreader.NewWith(r)
	if err != nil {
		return fmt.Errorf("opening ogg/opus stream: %w", err)
	}

	var lastGranule uint64
	packetCount := 0
	startTime := time.Now()

	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		payload, hdr, err := rd.ParseNextPage()
		if err != nil {
			if errors.Is(err, io.EOF) {
				return nil
			}
			if packetCount == 0 {
				log("audio: no packets after %v: %v", time.Since(startTime), err)
			}
			return err
		}

		// Granule position is in 48kHz samples. The first audio page may carry
		// multiple frames; size by delta and emit as a single sample. ffmpeg
		// with `-frame_duration 20` keeps that delta at 960 samples / 20ms,
		// but if a page bunches frames we still cover the right duration.
		dur := audioFrameDuration
		if hdr.GranulePosition > lastGranule {
			samples := hdr.GranulePosition - lastGranule
			dur = time.Duration(samples) * time.Second / 48000
			lastGranule = hdr.GranulePosition
		}
		if len(payload) == 0 {
			continue
		}

		if err := track.WriteSample(media.Sample{Data: payload, Duration: dur}); err != nil {
			log("audio WriteSample error: %v", err)
			return err
		}

		packetCount++
		if packetCount == 1 {
			log("audio: first packet after %v", time.Since(startTime))
		}
		if packetCount%500 == 0 {
			log("audio: %d packets sent", packetCount)
		}
	}
}

// logf is a tiny adapter so capture functions can accept any Printf-like logger.
type logf func(format string, v ...any)

// runAudioPipeline is the glue used by audio_linux.go / audio_windows.go.
// It spawns ffmpeg with the supplied arguments, hooks the Opus track up to
// the ogg/opus stream on stdout, and arranges cleanup on ctx.Done().
func runAudioPipeline(ctx context.Context, t Target, ffmpegArgs []string) error {
	if t.AddAudioTrack == nil {
		return ErrAudioUnavailable
	}

	track, err := newOpusTrack()
	if err != nil {
		return fmt.Errorf("creating opus audio track: %w", err)
	}
	if err := t.AddAudioTrack(track); err != nil {
		return fmt.Errorf("adding audio track: %w", err)
	}

	cmd := exec.CommandContext(ctx, "ffmpeg", ffmpegArgs...)

	var ffmpegLog io.Writer = io.Discard
	if t.LogFile != nil {
		ffmpegLog = t.LogFile
	}
	cmd.Stderr = ffmpegLog

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return fmt.Errorf("audio stdout pipe: %w", err)
	}
	if err := cmd.Start(); err != nil {
		return fmt.Errorf("starting audio ffmpeg: %w", err)
	}

	t.Logger.Printf("audio capture started (pid=%d)", cmd.Process.Pid)

	go func() {
		<-ctx.Done()
		if cmd.Process != nil {
			_ = cmd.Process.Kill()
		}
		_ = stdout.Close()
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
