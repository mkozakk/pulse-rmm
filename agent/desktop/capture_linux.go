//go:build linux

package desktop

import (
	"context"
	"errors"
	"fmt"
	"image"
	"os"
	"time"

	"github.com/jezek/xgb"
	"github.com/jezek/xgb/xproto"
	"github.com/pion/mediadevices/pkg/codec/vpx"
	"github.com/pion/mediadevices/pkg/prop"
	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
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
	if os.Getenv("DISPLAY") == "" {
		return errors.New("no X11 display (DISPLAY not set)")
	}

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
	sess.log.Println("XGetImage OK, setting up encoder")

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

	// Watcher: close X11 conn as soon as context is cancelled so that any
	// in-flight XGetImage inside encoder.Read() fails immediately instead of
	// blocking until the next frame boundary.
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
				return // conn closed by watcher → XGetImage failed → encoder returns error
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

	sess.log.Println("capture started successfully")
	return nil
}

// x11Reader implements prop.VideoReader by capturing the root window via XGetImage.
// No XShm required — works on VMs and remote X sessions.
// The rate limiter prevents the encoder's internal goroutine from spinning at full
// CPU speed; closing conn causes the next Read() to fail, unblocking Close().
type x11Reader struct {
	conn    *xgb.Conn
	root    xproto.Window
	w, h    int
	limiter <-chan time.Time
}

func (r *x11Reader) Read() (img image.Image, release func(), err error) {
	<-r.limiter // rate-limit to 15 fps
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
