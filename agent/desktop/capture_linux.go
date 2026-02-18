//go:build linux

package desktop

import (
	"context"
	"errors"
	"fmt"
	"image"
	"io"
	"log"
	"os"
	"os/exec"
	"time"

	"github.com/jezek/xgb"
	"github.com/jezek/xgb/xproto"
	"github.com/pion/mediadevices/pkg/codec/vpx"
	"github.com/pion/mediadevices/pkg/prop"
	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
	"github.com/pion/webrtc/v4/pkg/media/h264reader"
)

func checkPlatform() error {
	if os.Getenv("WAYLAND_DISPLAY") != "" {
		return ErrWaylandNotSupported
	}
	return nil
}

func startCapture(sess *DesktopSession, ctx context.Context) error {
	if err := checkPlatform(); err != nil {
		return err
	}
	display := os.Getenv("DISPLAY")
	if display == "" {
		return errors.New("no X11 display (DISPLAY not set)")
	}

	codec := selectBestH264Codec(sess.log, display)
	if codec != "" {
		return startH264Capture(sess, ctx, display, codec)
	}
	sess.log.Println("no H264 encoder available, falling back to VP9")
	return startVP9Capture(sess, ctx)
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

func startVP9Capture(sess *DesktopSession, ctx context.Context) error {
	sess.log.Printf("connecting to X11 display %s", os.Getenv("DISPLAY"))
	conn, err := xgb.NewConn()
	if err != nil {
		return fmt.Errorf("connecting to X11: %w", err)
	}

	setup := xproto.Setup(conn)
	screen := setup.DefaultScreen(conn)
	root := screen.Root
	w := int(screen.WidthInPixels)
	h := int(screen.HeightInPixels)
	sess.log.Printf("screen size: %dx%d", w, h)

	sess.log.Println("testing XGetImage...")
	testCh := make(chan error, 1)
	go func() {
		_, e := xproto.GetImage(conn, xproto.ImageFormatZPixmap, xproto.Drawable(root), 0, 0, uint16(w), uint16(h), 0xffffffff).Reply()
		testCh <- e
	}()
	select {
	case err := <-testCh:
		if err != nil {
			conn.Close()
			return fmt.Errorf("XGetImage test failed: %w", err)
		}
	case <-time.After(5 * time.Second):
		conn.Close()
		return errors.New("XGetImage timed out — X server may not be accessible (check XAUTHORITY)")
	}
	sess.log.Println("XGetImage OK, setting up VP9 encoder")

	vpxParams, err := vpx.NewVP9Params()
	if err != nil {
		conn.Close()
		return fmt.Errorf("creating VP9 params: %w", err)
	}
	vpxParams.BitRate = 2_500_000

	reader := &x11Reader{conn: conn, root: root, w: w, h: h, limiter: time.NewTicker(time.Second / 30).C}
	encoder, err := vpxParams.BuildVideoEncoder(reader, prop.Media{
		Video: prop.Video{Width: w, Height: h, FrameRate: 30},
	})
	if err != nil {
		conn.Close()
		return fmt.Errorf("building VP9 encoder: %w", err)
	}

	track, err := webrtc.NewTrackLocalStaticSample(webrtc.RTPCodecCapability{
		MimeType:  webrtc.MimeTypeVP9,
		ClockRate: 90000,
	}, "video", "desktop")
	if err != nil {
		encoder.Close()
		conn.Close()
		return fmt.Errorf("creating video track: %w", err)
	}

	if err := sess.addVideoTrack(track); err != nil {
		encoder.Close()
		conn.Close()
		return fmt.Errorf("adding video track: %w", err)
	}

	go func() {
		<-ctx.Done()
		conn.Close()
	}()

	go func() {
		defer encoder.Close()
		const frameDur = time.Second / 30
		for {
			pkt, release, err := encoder.Read()
			if err != nil {
				return
			}
			if err := track.WriteSample(media.Sample{
				Data:     pkt,
				Duration: frameDur,
			}); err != nil {
				release()
				return
			}
			release()
		}
	}()

	sess.log.Println("VP9 capture started")
	return nil
}

// x11Reader implements prop.VideoReader by capturing the root window via XGetImage.
// No XShm required — works on VMs and remote X sessions.
type x11Reader struct {
	conn    *xgb.Conn
	root    xproto.Window
	w, h    int
	limiter <-chan time.Time
}

func (r *x11Reader) Read() (img image.Image, release func(), err error) {
	<-r.limiter
	reply, err := xproto.GetImage(r.conn, xproto.ImageFormatZPixmap, xproto.Drawable(r.root),
		0, 0, uint16(r.w), uint16(r.h), 0xffffffff).Reply()
	if err != nil {
		return nil, func() {}, fmt.Errorf("XGetImage: %w", err)
	}

	// X11 ZPixmap is BGRA (or BGRX). Convert to RGBA for the VP9 encoder.
	out := image.NewNRGBA(image.Rect(0, 0, r.w, r.h))
	src := reply.Data
	dst := out.Pix
	for i := 0; i+3 < len(src) && i+3 < len(dst); i += 4 {
		dst[i+0] = src[i+2] // R ← B
		dst[i+1] = src[i+1] // G
		dst[i+2] = src[i+0] // B ← R
		dst[i+3] = 0xff      // A
	}
	return out, func() {}, nil
}
