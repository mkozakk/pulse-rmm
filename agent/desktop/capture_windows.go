//go:build windows

package desktop

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log"
	"os"
	"os/exec"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
	"github.com/pion/webrtc/v4/pkg/media/h264reader"
)

type frameCounter struct{ n atomic.Int64 }

func (f *frameCounter) set(v int)  { f.n.Store(int64(v)) }
func (f *frameCounter) get() int64 { return f.n.Load() }

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
		SDPFmtpLine: "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e02a",
	}, "video", "desktop")
	if err != nil {
		return fmt.Errorf("creating H264 video track: %w", err)
	}

	if err := sess.addVideoTrack(track); err != nil {
		return fmt.Errorf("adding video track: %w", err)
	}

	codec := selectBestH264Codec(sess.log)
	sess.log.Printf("Using H264 codec: %s", codec)

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

	// Force Baseline 4.2 profile to match SDP profile-level-id=42e02a.
	// Chrome silently drops frames if negotiated profile != actual SPS profile.
	switch codec {
	case "h264_nvenc":
		ffmpegArgs = append(ffmpegArgs,
			"-preset", "fast",
			"-profile:v", "baseline",
			"-level", "4.2",
		)
	case "h264_qsv":
		ffmpegArgs = append(ffmpegArgs,
			"-preset", "veryfast",
			"-profile:v", "baseline",
			"-level", "4.2",
		)
	case "libx264":
		ffmpegArgs = append(ffmpegArgs,
			"-preset", "veryfast",
			"-tune", "zerolatency",
			"-profile:v", "baseline",
			"-level", "4.2",
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
	sess.log.Printf("ffmpeg cmdline: ffmpeg %v", ffmpegArgs)

	var ffmpegLog io.Writer = os.Stderr
	if sess.logFile != nil {
		ffmpegLog = io.MultiWriter(os.Stderr, sess.logFile)
	}
	cmd.Stderr = ffmpegLog

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return fmt.Errorf("creating stdout pipe: %w", err)
	}

	if err := cmd.Start(); err != nil {
		return fmt.Errorf("starting ffmpeg: %w", err)
	}

	killFFmpeg := func() {
		if cmd.Process != nil {
			_ = cmd.Process.Kill()
		}
	}

	sess.log.Printf("H264 capture started (FFmpeg + gdigrab), pid=%d", cmd.Process.Pid)

	go func() {
		<-ctx.Done()
		sess.log.Printf("ctx cancelled, killing ffmpeg pid=%d", cmd.Process.Pid)
		killFFmpeg()
		_ = stdout.Close()
	}()

	// Watchdog: log frame production status every 5s. If after 10s no frame, dump a clear diagnostic.
	frameCounter := &frameCounter{}
	go func() {
		ticker := time.NewTicker(5 * time.Second)
		defer ticker.Stop()
		warned := false
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				n := frameCounter.get()
				sess.log.Printf("watchdog: frames produced so far = %d", n)
				if n == 0 && !warned {
					sess.log.Printf("WARNING: no video frames produced after 5s. Common cause on Windows: agent runs as SYSTEM service in Session 0 and gdigrab cannot see the user's desktop. Check ffmpeg stderr lines above for capture errors.")
					warned = true
				}
			}
		}
	}()

	go func() {
		defer func() {
			killFFmpeg()
			_ = stdout.Close()
			waitErr := cmd.Wait()
			sess.log.Printf("capture stopped (ffmpeg exit: %v)", waitErr)
		}()

		sess.log.Println("Waiting for H264 stream...")
		startTime := time.Now()

		h264r, err := h264reader.NewReader(stdout)
		if err != nil {
			sess.log.Printf("h264reader init error: %v", err)
			return
		}

		frameCount := 0
		const frameDur = time.Second / 30
		startCode := []byte{0x00, 0x00, 0x00, 0x01}

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
					sess.log.Printf("No frames produced after %v: %v", time.Since(startTime), err)
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
				sess.log.Printf("WriteSample error: %v", err)
				return
			}
			au = au[:0]

			frameCount++
			frameCounter.set(frameCount)
			if frameCount == 1 {
				sess.log.Printf("First frame after %v (size=%d bytes)", time.Since(startTime), len(au))
			}
			if frameCount%30 == 0 {
				sess.log.Printf("%d H264 frames sent", frameCount)
			}
		}
	}()

	return nil
}

func selectBestH264Codec(logger *log.Logger) string {
	for _, codec := range []string{"h264_nvenc", "h264_qsv"} {
		if tryH264Codec(codec) {
			logger.Printf("hardware codec %s available", codec)
			return codec
		}
		logger.Printf("hardware codec %s unavailable", codec)
	}
	logger.Printf("using libx264 (software H264)")
	return "libx264"
}

// tryH264Codec tests if a specific H264 encoder is available
func tryH264Codec(codec string) bool {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	args := []string{
		"-hide_banner",
		"-loglevel", "error",
		"-f", "gdigrab",
		"-framerate", "30",
		"-i", "desktop",
		"-c:v", codec,
	}
	if codec == "h264_nvenc" {
		args = append(args, "-preset", "fast")
	}
	if codec == "h264_qsv" {
		args = append(args, "-preset", "veryfast")
	}
	args = append(args,
		"-profile:v", "baseline",
		"-level", "4.2",
		"-g", "30",
		"-pix_fmt", "yuv420p",
		"-frames:v", "5",
		"-fflags", "+genpts",
		"-f", "null",
		"NUL",
	)

	cmd := exec.CommandContext(ctx, "ffmpeg", args...)

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
