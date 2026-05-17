//go:build linux

package capture

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log"
	"os"
	"os/exec"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
	"github.com/pion/webrtc/v4/pkg/media/h264reader"
)

// Start dispatches to the right capture backend for the current Linux session.
// Under Wayland it uses PipeWire; under X11 it uses x11grab via ffmpeg.
func Start(ctx context.Context, t Target) error {
	t.Logger.Printf("capture env: %s", DescribeSessionEnv())
	if IsWaylandSession() {
		t.Logger.Println("dispatching capture: pipewire (Wayland)")
		return startPipeWireCapture(ctx, t)
	}
	if os.Getenv("DISPLAY") == "" {
		return errors.New("no Wayland session detected and DISPLAY not set — helper has no graphical session to capture")
	}
	t.Logger.Println("dispatching capture: x11grab")
	return startX11Capture(ctx, t)
}

func startX11Capture(ctx context.Context, t Target) error {
	display := os.Getenv("DISPLAY")
	codec := selectBestH264Codec(t.Logger, display)
	if codec == "" {
		return fmt.Errorf("no H264 encoder available — install ffmpeg with libx264, h264_vaapi, or h264_nvenc support")
	}
	return startH264Capture(ctx, t, display, codec)
}

// StartKMS is the kmsgrab path used when the system service runs as root with
// no user session. Platform code calls this directly instead of Start.
func StartKMS(ctx context.Context, t Target) error {
	sessionType := os.Getenv("XDG_SESSION_TYPE")
	t.Logger.Printf("starting kmsgrab capture (XDG_SESSION_TYPE=%q)", sessionType)

	if sessionType == "wayland" {
		return fmt.Errorf("wayland session detected — kmsgrab cannot capture under a Wayland compositor (it holds DRM master); PipeWire portal support is not yet implemented")
	}

	if _, err := exec.LookPath("ffmpeg"); err != nil {
		return fmt.Errorf("ffmpeg not found — required for kmsgrab capture")
	}

	codec := selectBestKmsCodec(t.Logger)
	if codec == "" {
		return fmt.Errorf("kmsgrab: no suitable codec — ensure agent is in 'video' group and VAAPI is available (/dev/dri/renderD128)")
	}

	track, err := webrtc.NewTrackLocalStaticSample(webrtc.RTPCodecCapability{
		MimeType:    webrtc.MimeTypeH264,
		ClockRate:   90000,
		SDPFmtpLine: "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e02a",
	}, "video", "desktop")
	if err != nil {
		return fmt.Errorf("creating H264 video track: %w", err)
	}
	if err := t.AddTrack(track); err != nil {
		return fmt.Errorf("adding video track: %w", err)
	}

	t.Logger.Printf("kmsgrab: using codec %s", codec)

	args := []string{
		"-fflags", "+nobuffer",
		"-flags", "+low_delay",
	}
	if codec == "h264_vaapi" {
		args = append(args, "-vaapi_device", "/dev/dri/renderD128")
	}
	args = append(args,
		"-device", "/dev/dri/card0",
		"-f", "kmsgrab",
		"-framerate", "30",
		"-i", "-",
		"-c:v", codec,
		"-b:v", "8000k",
		"-maxrate", "10000k",
		"-bufsize", "10000k",
		"-r", "30",
		"-g", "30",
	)
	switch codec {
	case "h264_vaapi":
		args = append(args,
			"-vf", "hwmap=derive_device=vaapi,scale_vaapi=format=nv12",
			"-profile:v", "constrained_baseline",
			"-level", "4.2",
		)
	case "libx264":
		args = append(args,
			"-preset", "veryfast",
			"-tune", "zerolatency",
			"-profile:v", "baseline",
			"-level", "4.2",
			"-pix_fmt", "yuv420p",
			"-x264-params", "slices=1:sliced-threads=0",
		)
	}
	args = append(args,
		"-bsf:v", "dump_extra",
		"-f", "h264",
		"pipe:1",
	)

	cmd := exec.CommandContext(ctx, "ffmpeg", args...)

	var ffmpegLog io.Writer = os.Stderr
	if t.LogFile != nil {
		ffmpegLog = io.MultiWriter(os.Stderr, t.LogFile)
	}
	cmd.Stderr = ffmpegLog

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return fmt.Errorf("creating stdout pipe: %w", err)
	}
	if err := cmd.Start(); err != nil {
		return fmt.Errorf("starting ffmpeg kmsgrab: %w", err)
	}

	killFFmpeg := func() {
		if cmd.Process != nil {
			_ = cmd.Process.Kill()
		}
	}

	t.Logger.Println("kmsgrab capture started")

	go func() {
		<-ctx.Done()
		killFFmpeg()
		_ = stdout.Close()
	}()

	go func() {
		defer func() {
			killFFmpeg()
			_ = stdout.Close()
			_ = cmd.Wait()
			t.Logger.Println("kmsgrab capture stopped")
		}()

		startTime := time.Now()
		h264r, err := h264reader.NewReader(stdout)
		if err != nil {
			t.Logger.Printf("h264reader init error: %v", err)
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
					t.Logger.Printf("kmsgrab: no frames after %v: %v", time.Since(startTime), err)
				}
				return
			}

			if len(nal.Data) == 0 {
				continue
			}

			au = append(au, startCode...)
			au = append(au, nal.Data...)

			if nal.UnitType != h264reader.NalUnitTypeCodedSliceNonIdr &&
				nal.UnitType != h264reader.NalUnitTypeCodedSliceIdr {
				continue
			}

			if err := track.WriteSample(media.Sample{Data: au, Duration: frameDur}); err != nil {
				t.Logger.Printf("WriteSample error: %v", err)
				return
			}
			au = au[:0]

			frameCount++
			if frameCount == 1 {
				t.Logger.Printf("kmsgrab: first frame after %v", time.Since(startTime))
			}
			if frameCount%30 == 0 {
				t.Logger.Printf("kmsgrab: %d frames sent", frameCount)
			}
		}
	}()

	return nil
}

func selectBestKmsCodec(logger *log.Logger) string {
	if tryKmsCodec("h264_vaapi") {
		logger.Println("kmsgrab: hardware codec h264_vaapi available")
		return "h264_vaapi"
	}
	logger.Println("kmsgrab: h264_vaapi unavailable")
	if tryKmsCodec("libx264") {
		logger.Println("kmsgrab: using libx264 (software)")
		return "libx264"
	}
	logger.Println("kmsgrab: libx264 unavailable")
	return ""
}

func tryKmsCodec(codec string) bool {
	// 5s: VMs can be slow to initialise kmsgrab
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	args := []string{"-hide_banner", "-loglevel", "error"}
	if codec == "h264_vaapi" {
		args = append(args, "-vaapi_device", "/dev/dri/renderD128")
	}
	args = append(args,
		"-device", "/dev/dri/card0",
		"-f", "kmsgrab",
		"-framerate", "5",
		"-i", "-",
		"-c:v", codec,
	)
	switch codec {
	case "h264_vaapi":
		args = append(args,
			"-vf", "hwmap=derive_device=vaapi,scale_vaapi=format=nv12",
			"-profile:v", "constrained_baseline",
			"-level", "4.2",
		)
	case "libx264":
		// ffmpeg auto-converts drm_prime → yuv420p; hwdownload is not needed
		// and breaks on VMs that lack a full DRM hardware context.
		args = append(args,
			"-preset", "veryfast",
			"-profile:v", "baseline",
			"-level", "4.2",
			"-pix_fmt", "yuv420p",
		)
	}
	args = append(args, "-frames:v", "5", "-f", "null", "/dev/null")

	cmd := exec.CommandContext(ctx, "ffmpeg", args...)
	cmd.Stderr = io.Discard
	cmd.Stdout = io.Discard
	return cmd.Run() == nil
}

func selectBestH264Codec(logger *log.Logger, display string) string {
	if _, err := exec.LookPath("ffmpeg"); err != nil {
		logger.Println("ffmpeg not found — H264 unavailable, falling back to VP9")
		return ""
	}
	for _, codec := range []string{"h264_vaapi", "h264_nvenc"} {
		if tryH264Codec(codec, display) {
			logger.Printf("hardware codec %s available", codec)
			return codec
		}
		logger.Printf("hardware codec %s unavailable", codec)
	}
	if tryH264Codec("libx264", display) {
		logger.Println("using libx264 (software H264)")
		return "libx264"
	}
	logger.Println("no H264 encoder found")
	return ""
}

func tryH264Codec(codec, display string) bool {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	args := []string{
		"-hide_banner",
		"-loglevel", "error",
	}

	if codec == "h264_vaapi" {
		args = append(args, "-vaapi_device", "/dev/dri/renderD128")
	}

	args = append(args,
		"-f", "x11grab",
		"-framerate", "30",
		"-i", display,
		"-c:v", codec,
	)

	switch codec {
	case "h264_vaapi":
		args = append(args,
			"-vf", "format=nv12,hwupload",
			"-profile:v", "constrained_baseline",
			"-level", "4.2",
		)
	case "h264_nvenc":
		args = append(args,
			"-preset", "fast",
			"-profile:v", "baseline",
			"-level", "4.2",
			"-pix_fmt", "yuv420p",
		)
	case "libx264":
		args = append(args,
			"-preset", "veryfast",
			"-profile:v", "baseline",
			"-level", "4.2",
			"-pix_fmt", "yuv420p",
		)
	}

	args = append(args,
		"-g", "30",
		"-frames:v", "5",
		"-f", "null",
		"/dev/null",
	)

	cmd := exec.CommandContext(ctx, "ffmpeg", args...)
	cmd.Stderr = io.Discard
	cmd.Stdout = io.Discard
	return cmd.Run() == nil
}

func startH264Capture(ctx context.Context, t Target, display, codec string) error {
	track, err := webrtc.NewTrackLocalStaticSample(webrtc.RTPCodecCapability{
		MimeType:    webrtc.MimeTypeH264,
		ClockRate:   90000,
		SDPFmtpLine: "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e02a",
	}, "video", "desktop")
	if err != nil {
		return fmt.Errorf("creating H264 video track: %w", err)
	}

	if err := t.AddTrack(track); err != nil {
		return fmt.Errorf("adding video track: %w", err)
	}

	t.Logger.Printf("Using H264 codec: %s", codec)

	args := []string{
		"-fflags", "+nobuffer",
		"-flags", "+low_delay",
	}

	if codec == "h264_vaapi" {
		args = append(args, "-vaapi_device", "/dev/dri/renderD128")
	}

	args = append(args,
		"-f", "x11grab",
		"-framerate", "30",
		"-i", display,
		"-c:v", codec,
		"-b:v", "8000k",
		"-maxrate", "10000k",
		"-bufsize", "10000k",
		"-r", "30",
		"-g", "30",
	)

	switch codec {
	case "h264_vaapi":
		args = append(args,
			"-vf", "format=nv12,hwupload",
			"-profile:v", "constrained_baseline",
			"-level", "4.2",
		)
	case "h264_nvenc":
		args = append(args,
			"-preset", "fast",
			"-profile:v", "baseline",
			"-level", "4.2",
			"-pix_fmt", "yuv420p",
		)
	case "libx264":
		args = append(args,
			"-preset", "veryfast",
			"-tune", "zerolatency",
			"-profile:v", "baseline",
			"-level", "4.2",
			"-pix_fmt", "yuv420p",
			// Force single slice per frame — zerolatency tune enables sliced-threads
			// which splits frames across multiple NALs; our AU emitter drops the extras.
			"-x264-params", "slices=1:sliced-threads=0",
		)
	}

	args = append(args,
		"-bsf:v", "dump_extra",
		"-f", "h264",
		"pipe:1",
	)

	cmd := exec.CommandContext(ctx, "ffmpeg", args...)

	var ffmpegLog io.Writer = os.Stderr
	if t.LogFile != nil {
		ffmpegLog = io.MultiWriter(os.Stderr, t.LogFile)
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

	t.Logger.Println("H264 capture started (FFmpeg + x11grab)")

	go func() {
		<-ctx.Done()
		killFFmpeg()
		_ = stdout.Close()
	}()

	go func() {
		defer func() {
			killFFmpeg()
			_ = stdout.Close()
			_ = cmd.Wait()
			t.Logger.Println("capture stopped")
		}()

		t.Logger.Println("Waiting for H264 stream...")
		startTime := time.Now()

		h264r, err := h264reader.NewReader(stdout)
		if err != nil {
			t.Logger.Printf("h264reader init error: %v", err)
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
					t.Logger.Printf("No frames produced after %v: %v", time.Since(startTime), err)
				}
				return
			}

			if len(nal.Data) == 0 {
				continue
			}

			au = append(au, startCode...)
			au = append(au, nal.Data...)

			if nal.UnitType != h264reader.NalUnitTypeCodedSliceNonIdr &&
				nal.UnitType != h264reader.NalUnitTypeCodedSliceIdr {
				continue
			}

			if err := track.WriteSample(media.Sample{
				Data:     au,
				Duration: frameDur,
			}); err != nil {
				t.Logger.Printf("WriteSample error: %v", err)
				return
			}
			au = au[:0]

			frameCount++
			if frameCount == 1 {
				t.Logger.Printf("First frame after %v", time.Since(startTime))
			}
			if frameCount%30 == 0 {
				t.Logger.Printf("%d H264 frames sent", frameCount)
			}
		}
	}()

	return nil
}
