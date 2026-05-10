//go:build linux

package desktop

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

func checkPlatform() error {
	if os.Getenv("WAYLAND_DISPLAY") == "" && os.Getenv("DISPLAY") == "" {
		return errors.New("no display available (DISPLAY and WAYLAND_DISPLAY both unset)")
	}
	return nil
}

func startCapture(sess *DesktopSession, ctx context.Context) error {
	if err := checkPlatform(); err != nil {
		return err
	}
	if os.Getenv("WAYLAND_DISPLAY") != "" {
		return startKmsCapture(sess, ctx)
	}
	display := os.Getenv("DISPLAY")
	codec := selectBestH264Codec(sess.log, display)
	if codec == "" {
		return fmt.Errorf("no H264 encoder available — install ffmpeg with libx264, h264_vaapi, or h264_nvenc support")
	}
	return startH264Capture(sess, ctx, display, codec)
}

// --- kmsgrab (Wayland) ---

func startKmsCapture(sess *DesktopSession, ctx context.Context) error {
	sess.log.Println("Wayland detected, starting kmsgrab capture")

	if _, err := exec.LookPath("ffmpeg"); err != nil {
		return fmt.Errorf("ffmpeg not found — required for Wayland capture")
	}

	codec := selectBestKmsCodec(sess.log)
	if codec == "" {
		return fmt.Errorf("kmsgrab: no suitable codec — ensure agent is in 'video' group and VAAPI is available (/dev/dri/renderD128)")
	}

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

	sess.log.Printf("kmsgrab: using codec %s", codec)

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
			"-level", "3.1",
		)
	case "libx264":
		args = append(args,
			"-vf", "hwmap=derive_device=vaapi,hwdownload,format=bgr0,format=yuv420p",
			"-preset", "veryfast",
			"-tune", "zerolatency",
			"-profile:v", "baseline",
			"-level", "3.1",
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
	if sess.logFile != nil {
		ffmpegLog = io.MultiWriter(os.Stderr, sess.logFile)
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

	sess.log.Println("kmsgrab capture started")

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
			sess.log.Println("kmsgrab capture stopped")
		}()

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
					sess.log.Printf("kmsgrab: no frames after %v: %v", time.Since(startTime), err)
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
				sess.log.Printf("WriteSample error: %v", err)
				return
			}
			au = au[:0]

			frameCount++
			if frameCount == 1 {
				sess.log.Printf("kmsgrab: first frame after %v", time.Since(startTime))
			}
			if frameCount%30 == 0 {
				sess.log.Printf("kmsgrab: %d frames sent", frameCount)
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
		logger.Println("kmsgrab: using libx264 (software, via VAAPI bridge)")
		return "libx264"
	}
	logger.Println("kmsgrab: libx264 unavailable")
	return ""
}

func tryKmsCodec(codec string) bool {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
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
			"-level", "3.1",
		)
	case "libx264":
		args = append(args,
			"-vf", "hwmap=derive_device=vaapi,hwdownload,format=bgr0,format=yuv420p",
			"-preset", "veryfast",
			"-profile:v", "baseline",
			"-level", "3.1",
			"-pix_fmt", "yuv420p",
		)
	}
	args = append(args, "-frames:v", "5", "-f", "null", "/dev/null")

	cmd := exec.CommandContext(ctx, "ffmpeg", args...)
	cmd.Stderr = io.Discard
	cmd.Stdout = io.Discard
	return cmd.Run() == nil
}

// --- x11grab + H264 ---

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
			"-level", "3.1",
		)
	case "h264_nvenc":
		args = append(args,
			"-preset", "fast",
			"-profile:v", "baseline",
			"-level", "3.1",
			"-pix_fmt", "yuv420p",
		)
	case "libx264":
		args = append(args,
			"-preset", "veryfast",
			"-profile:v", "baseline",
			"-level", "3.1",
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

func startH264Capture(sess *DesktopSession, ctx context.Context, display, codec string) error {
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

	sess.log.Printf("Using H264 codec: %s", codec)

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
			"-level", "3.1",
		)
	case "h264_nvenc":
		args = append(args,
			"-preset", "fast",
			"-profile:v", "baseline",
			"-level", "3.1",
			"-pix_fmt", "yuv420p",
		)
	case "libx264":
		args = append(args,
			"-preset", "veryfast",
			"-tune", "zerolatency",
			"-profile:v", "baseline",
			"-level", "3.1",
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

	sess.log.Println("H264 capture started (FFmpeg + x11grab)")

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
			sess.log.Println("capture stopped")
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
			if frameCount == 1 {
				sess.log.Printf("First frame after %v", time.Since(startTime))
			}
			if frameCount%30 == 0 {
				sess.log.Printf("%d H264 frames sent", frameCount)
			}
		}
	}()

	return nil
}

