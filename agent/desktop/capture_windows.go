//go:build windows

package desktop

import (
	"bufio"
	"context"
	"fmt"
	"io"
	"os"
	"os/exec"
	"syscall"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
)

func checkPlatform() error {
	_, err := exec.LookPath("ffmpeg")
	if err != nil {
		return fmt.Errorf("ffmpeg not found: install ffmpeg to enable video capture")
	}
	return nil
}

func startCapture(sess *DesktopSession, ctx context.Context) error {
	track, err := webrtc.NewTrackLocalStaticSample(webrtc.RTPCodecCapability{
		MimeType:  webrtc.MimeTypeVP9,
		ClockRate: 90000,
	}, "video", "desktop")
	if err != nil {
		return fmt.Errorf("creating VP9 video track: %w", err)
	}

	if err := sess.addVideoTrack(track); err != nil {
		return fmt.Errorf("adding video track: %w", err)
	}

	// Start FFmpeg: capture desktop with gdigrab, encode to VP9
	cmd := exec.CommandContext(ctx,
		"ffmpeg",
		"-fflags", "+nobuffer",      // disable output buffering
		"-f", "gdigrab",
		"-i", "desktop",
		"-c:v", "libvpx-vp9",
		"-b:v", "2500k",
		"-r", "30",
		"-pix_fmt", "yuv420p",
		"-f", "ivf",
		"-flvflags", "+nobuffer",    // minimize delay
		"pipe:1",
	)

	stderr := os.Stderr
	cmd.Stderr = stderr

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return fmt.Errorf("creating stdout pipe: %w", err)
	}

	if err := cmd.Start(); err != nil {
		return fmt.Errorf("starting ffmpeg: %w", err)
	}

	fmt.Println("[desktop] VP9 capture started (FFmpeg + gdigrab)")

	go func() {
		defer func() {
			cmd.Wait()
			fmt.Println("[desktop] Capture goroutine exiting")
		}()

		fmt.Println("[desktop] Waiting for FFmpeg output...")
		startTime := time.Now()

		reader := bufio.NewReaderSize(stdout, 256*1024)
		frameCount := 0
		const frameDur = time.Second / 30

		// Skip IVF file header (32 bytes)
		fmt.Println("[desktop] Reading IVF header...")
		header := make([]byte, 32)
		if _, err := io.ReadFull(reader, header); err != nil {
			fmt.Printf("[desktop] Error reading IVF header: %v\n", err)
			return
		}
		fmt.Printf("[desktop] IVF header received after %v\n", time.Since(startTime))

		for {
			select {
			case <-ctx.Done():
				return
			default:
			}

			// Read IVF frame header (12 bytes)
			frameHdr := make([]byte, 12)
			if _, err := io.ReadFull(reader, frameHdr); err != nil {
				if err != io.EOF {
					fmt.Printf("[desktop] Error reading frame header: %v\n", err)
				}
				return
			}

			// Parse frame size (little-endian at offset 0-3)
			frameSize := uint32(frameHdr[0]) |
				uint32(frameHdr[1])<<8 |
				uint32(frameHdr[2])<<16 |
				uint32(frameHdr[3])<<24

			if frameSize == 0 || frameSize > 1024*1024 {
				fmt.Printf("[desktop] Invalid frame size: %d\n", frameSize)
				return
			}

			// Read frame data
			frame := make([]byte, frameSize)
			if _, err := io.ReadFull(reader, frame); err != nil {
				fmt.Printf("[desktop] Error reading frame data: %v\n", err)
				return
			}

			if err := track.WriteSample(media.Sample{
				Data:     frame,
				Duration: frameDur,
			}); err != nil {
				fmt.Printf("[desktop] WriteSample error: %v\n", err)
				return
			}

			frameCount++
			if frameCount%30 == 0 {
				fmt.Printf("[desktop] %d frames sent\n", frameCount)
			}
		}
	}()

	return nil
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
