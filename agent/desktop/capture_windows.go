//go:build windows

package desktop

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"syscall"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
	"github.com/pion/webrtc/v4/pkg/media/h264reader"
)

func checkPlatform() error {
	_, err := exec.LookPath("ffmpeg")
	if err != nil {
		return fmt.Errorf("ffmpeg not found: install ffmpeg to enable video capture")
	}
	return nil
}

func startCapture(sess *DesktopSession, ctx context.Context) error {
	// Create H264 video track
	track, err := webrtc.NewTrackLocalStaticSample(webrtc.RTPCodecCapability{
		MimeType:    webrtc.MimeTypeH264,
		ClockRate:   90000,
		SDPFmtpLine: "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f",
	}, "video", "desktop")
	if err != nil {
		return fmt.Errorf("creating H264 video track: %w", err)
	}

	if err := sess.addVideoTrack(track); err != nil {
		return fmt.Errorf("adding video track: %w", err)
	}

	// Use H264 with fallback to software libx264 if hardware unavailable
	codec := selectBestH264Codec()
	fmt.Printf("[desktop] Using H264 codec: %s\n", codec)

	// Build FFmpeg command for H264
	ffmpegArgs := []string{
		"-fflags", "+nobuffer",
		"-flags", "+low_delay",
		"-f", "gdigrab",
		"-i", "desktop",
		"-c:v", codec,
		"-b:v", "8000k",
		"-maxrate", "10000k",
		"-bufsize", "10000k",
		"-r", "30",
		"-g", "30",                // Force keyframe every 30 frames (CRITICAL for Chrome)
		"-pix_fmt", "yuv420p",
	}

	// Force Baseline 3.1 profile to match SDP profile-level-id=42001f.
	// Chrome silently drops frames if negotiated profile != actual SPS profile.
	switch codec {
	case "h264_nvenc":
		ffmpegArgs = append(ffmpegArgs,
			"-preset", "fast",
			"-profile:v", "baseline",
			"-level", "3.1",
		)
	case "h264_qsv":
		ffmpegArgs = append(ffmpegArgs,
			"-preset", "veryfast",
			"-profile:v", "baseline",
			"-level", "3.1",
		)
	case "libx264":
		ffmpegArgs = append(ffmpegArgs,
			"-preset", "veryfast",
			"-tune", "zerolatency",
			"-profile:v", "baseline",
			"-level", "3.1",
			// Force single slice per frame. Default zerolatency tune enables
			// sliced-threads which splits a frame across slices/NALs; our AU
			// emitter terminates on the first slice NAL and drops the rest.
			"-x264-params", "slices=1:sliced-threads=0",
		)
	}

	// dump_extra: prepend SPS/PPS before every keyframe so a late-joining
	// browser can initialize its decoder without waiting for stream start.
	ffmpegArgs = append(ffmpegArgs,
		"-bsf:v", "dump_extra",
		"-f", "h264",
		"pipe:1",
	)

	cmd := exec.CommandContext(ctx, "ffmpeg", ffmpegArgs...)

	stderr := os.Stderr
	cmd.Stderr = stderr

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return fmt.Errorf("creating stdout pipe: %w", err)
	}

	if err := cmd.Start(); err != nil {
		return fmt.Errorf("starting ffmpeg: %w", err)
	}

	fmt.Println("[desktop] H264 capture started (FFmpeg + gdigrab)")

	go func() {
		defer func() {
			cmd.Wait()
			fmt.Println("[desktop] Capture goroutine exiting")
		}()

		fmt.Println("[desktop] Waiting for H264 stream...")
		startTime := time.Now()

		h264r, err := h264reader.NewReader(stdout)
		if err != nil {
			fmt.Printf("[desktop] h264reader init error: %v\n", err)
			return
		}

		frameCount := 0
		const frameDur = time.Second / 30
		startCode := []byte{0x00, 0x00, 0x00, 0x01}

		// Accumulate NAL units of one access unit, emit when we see a slice NAL.
		// Pion's H264 payloader splits Annex-B into RTP packets with the same timestamp.
		var au []byte

		for {
			select {
			case <-ctx.Done():
				return
			default:
			}

			nal, err := h264r.NextNAL()
			if err != nil {
				if errors.Is(err, io.EOF) {
					return
				}
				if frameCount == 0 {
					fmt.Printf("[desktop] No frames produced after %v: %v\n", time.Since(startTime), err)
				}
				return
			}

			if len(nal.Data) == 0 {
				continue
			}

			au = append(au, startCode...)
			au = append(au, nal.Data...)

			// UnitType 1 = non-IDR slice, 5 = IDR slice. These terminate an access unit.
			if nal.UnitType != h264reader.NalUnitTypeCodedSliceNonIdr &&
				nal.UnitType != h264reader.NalUnitTypeCodedSliceIdr {
				continue
			}

			if err := track.WriteSample(media.Sample{
				Data:     au,
				Duration: frameDur,
			}); err != nil {
				fmt.Printf("[desktop] WriteSample error: %v\n", err)
				return
			}
			au = au[:0]

			frameCount++
			if frameCount == 1 {
				fmt.Printf("[desktop] First frame after %v\n", time.Since(startTime))
			}
			if frameCount%30 == 0 {
				fmt.Printf("[desktop] %d H264 frames sent\n", frameCount)
			}
		}
	}()

	return nil
}

// selectBestH264Codec tries hardware encoders first, falls back to libx264
func selectBestH264Codec() string {
	// Try hardware encoders in order
	for _, codec := range []string{"h264_nvenc", "h264_qsv"} {
		if tryH264Codec(codec) {
			fmt.Printf("[desktop] ✓ Hardware codec %s available\n", codec)
			return codec
		}
		fmt.Printf("[desktop] ✗ Hardware codec %s unavailable\n", codec)
	}

	// Always fall back to libx264 (software, always available)
	fmt.Println("[desktop] ✓ Using libx264 (software H264)")
	return "libx264"
}

// tryH264Codec tests if a specific H264 encoder is available
func tryH264Codec(codec string) bool {
	cmd := exec.Command("ffmpeg",
		"-f", "gdigrab",
		"-i", "desktop",
		"-c:v", codec,
		"-t", "0.05",
		"-f", "null",
		"-",
	)

	cmd.Stderr = io.Discard
	cmd.Stdout = io.Discard
	return cmd.Run() == nil
}

// Windows GDI API for potential future use
var (
	gdi32 = syscall.NewLazyDLL("gdi32.dll")
)

var (
	procCreateCompatibleDC   = gdi32.NewProc("CreateCompatibleDC")
	procCreateCompatibleBitmap = gdi32.NewProc("CreateCompatibleBitmap")
	procDeleteObject         = gdi32.NewProc("DeleteObject")
	procDeleteDC             = gdi32.NewProc("DeleteDC")
)

const (
	SRCCOPY       = 0x00CC0020
	DIB_RGB_COLORS = 0
	BI_RGB        = 0
)
