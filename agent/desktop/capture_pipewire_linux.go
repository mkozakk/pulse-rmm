//go:build linux

package desktop

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
	"github.com/pion/webrtc/v4/pkg/media/h264reader"
)

// startPipeWireCapture is the Wayland capture path. It walks the portal flow
// (prompting the user once), then picks a capture backend that can speak to a
// PipeWire node. Backend preference: GStreamer first (Debian/Ubuntu ship
// gstreamer1.0-pipewire reliably), then ffmpeg with pipewiregrab (rare —
// requires ffmpeg built with --enable-libpipewire).
func startPipeWireCapture(sess *DesktopSession, ctx context.Context) error {
	// The portal Start step blocks on user consent. Give the user 60s to
	// click "Share" before bailing out.
	consentCtx, consentCancel := context.WithTimeout(ctx, 60*time.Second)
	sc, err := openScreencast(consentCtx)
	consentCancel()
	if err != nil {
		return err
	}

	if len(sc.nodeIDs) == 0 {
		sc.Close()
		return errPortalNoStream
	}
	nodeID := sc.nodeIDs[0]
	sess.log.Printf("pipewire: portal granted node id %d", nodeID)

	backend, cmd, err := selectPipeWireBackend(ctx, sess, nodeID)
	if err != nil {
		sc.Close()
		return err
	}
	sess.log.Printf("pipewire: using backend %s", backend)

	track, err := webrtc.NewTrackLocalStaticSample(webrtc.RTPCodecCapability{
		MimeType:    webrtc.MimeTypeH264,
		ClockRate:   90000,
		SDPFmtpLine: "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e02a",
	}, "video", "desktop")
	if err != nil {
		sc.Close()
		return fmt.Errorf("creating H264 video track: %w", err)
	}
	if err := sess.addVideoTrack(track); err != nil {
		sc.Close()
		return fmt.Errorf("adding video track: %w", err)
	}

	var encLog io.Writer = os.Stderr
	if sess.logFile != nil {
		encLog = io.MultiWriter(os.Stderr, sess.logFile)
	}
	cmd.Stderr = encLog

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		sc.Close()
		return fmt.Errorf("creating stdout pipe: %w", err)
	}
	if err := cmd.Start(); err != nil {
		sc.Close()
		return fmt.Errorf("starting %s pipewire capture: %w", backend, err)
	}

	killEncoder := func() {
		if cmd.Process != nil {
			_ = cmd.Process.Kill()
		}
	}

	go func() {
		<-ctx.Done()
		killEncoder()
		_ = stdout.Close()
	}()

	go func() {
		defer func() {
			killEncoder()
			_ = stdout.Close()
			_ = cmd.Wait()
			sc.Close()
			sess.log.Println("pipewire capture stopped")
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
					sess.log.Printf("pipewire: no frames after %v: %v", time.Since(startTime), err)
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
				sess.log.Printf("pipewire: first frame after %v", time.Since(startTime))
			}
			if frameCount%30 == 0 {
				sess.log.Printf("pipewire: %d frames sent", frameCount)
			}
		}
	}()

	sess.log.Println("pipewire capture started")
	return nil
}

// selectPipeWireBackend prefers GStreamer (widely packaged with PipeWire
// support) and falls back to ffmpeg pipewiregrab (rare). Returns the
// constructed but not-yet-started command.
func selectPipeWireBackend(ctx context.Context, sess *DesktopSession, nodeID uint32) (string, *exec.Cmd, error) {
	if gstreamerHasPipeWire() {
		args := pipeWireGStreamerArgs(nodeID)
		return "gstreamer", exec.CommandContext(ctx, "gst-launch-1.0", args...), nil
	}
	sess.log.Println("pipewire: gst-launch-1.0 with pipewiresrc not available, trying ffmpeg")

	if _, err := exec.LookPath("ffmpeg"); err == nil && ffmpegHasPipeWire() {
		args := pipeWireFFmpegArgs("libx264", nodeID)
		return "ffmpeg", exec.CommandContext(ctx, "ffmpeg", args...), nil
	}

	return "", nil, errFFmpegNoPipeWire
}

// pipeWireGStreamerArgs builds a low-latency H264 pipeline that reads from a
// portal-granted PipeWire node and emits an Annex-B byte-stream on stdout —
// the exact format h264reader expects.
//
//   - videorate forces a constant 30fps stream because pipewiresrc only
//     emits frames on damage; an idle desktop produces ~0fps which keeps the
//     browser on a black canvas.
//   - key-int-max + force-keyframe via x264enc options give us an IDR every
//     second so WebRTC PLI requests on an idle screen still get a refresh.
func pipeWireGStreamerArgs(nodeID uint32) []string {
	// Force system memory (DMA-BUF frames from pipewiresrc only have the
	// rendered region valid — under fractional HiDPI scaling that leaves half
	// the buffer as uninitialised memory which the encoder then encodes as
	// noise). videoconvertscale + an explicit I420 cap reads the actual
	// pixels and normalises the format the encoder receives.
	pipeline := []string{
		"-q",
		"pipewiresrc", "path=" + strconv.FormatUint(uint64(nodeID), 10),
		"do-timestamp=true",
		"!", "video/x-raw",
		"!", "queue", "leaky=downstream", "max-size-buffers=2",
		"!", "videorate",
		"!", "videoconvert", "chroma-mode=none", "dither=0", "matrix-mode=output-only",
		"!", "videoscale",
		"!", "video/x-raw,format=I420,framerate=30/1,pixel-aspect-ratio=1/1",
		"!", "x264enc",
		"tune=zerolatency",
		"speed-preset=veryfast",
		"key-int-max=30",
		"bitrate=8000",
		"byte-stream=true",
		"threads=1",
		"sliced-threads=false",
		"!", "h264parse", "config-interval=-1",
		"!", "video/x-h264,stream-format=byte-stream,alignment=au,profile=baseline",
		"!", "fdsink", "fd=1", "sync=false",
	}
	return pipeline
}

// gstreamerHasPipeWire checks for gst-launch-1.0 + the pipewiresrc and
// x264enc elements. gst-inspect-1.0 exits 0 when the element is registered.
func gstreamerHasPipeWire() bool {
	if _, err := exec.LookPath("gst-launch-1.0"); err != nil {
		return false
	}
	if _, err := exec.LookPath("gst-inspect-1.0"); err != nil {
		return false
	}
	for _, el := range []string{"pipewiresrc", "x264enc", "h264parse"} {
		ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
		cmd := exec.CommandContext(ctx, "gst-inspect-1.0", "--exists", el)
		err := cmd.Run()
		cancel()
		if err != nil {
			// Older gst-inspect versions don't support --exists; fall back to
			// a plain inspection that prints the element factory.
			ctx2, cancel2 := context.WithTimeout(context.Background(), 3*time.Second)
			cmd2 := exec.CommandContext(ctx2, "gst-inspect-1.0", el)
			out, err2 := cmd2.CombinedOutput()
			cancel2()
			if err2 != nil || !strings.Contains(string(out), "Factory Details") {
				return false
			}
		}
	}
	return true
}

// pipeWireFFmpegArgs builds the ffmpeg invocation for a portal-granted
// PipeWire node. Kept for environments with ffmpeg --enable-libpipewire.
func pipeWireFFmpegArgs(codec string, nodeID uint32) []string {
	args := []string{
		"-fflags", "+nobuffer",
		"-flags", "+low_delay",
	}
	if codec == "h264_vaapi" {
		args = append(args, "-vaapi_device", "/dev/dri/renderD128")
	}
	args = append(args,
		"-f", "lavfi",
		"-i", "pipewiregrab=node="+strconv.FormatUint(uint64(nodeID), 10)+":framerate=30",
		"-c:v", codec,
		"-b:v", "8000k",
		"-maxrate", "10000k",
		"-bufsize", "10000k",
		"-r", "30",
		"-g", "30",
		"-keyint_min", "30",
		"-force_key_frames", "expr:gte(t,n_forced*1)",
	)
	switch codec {
	case "h264_vaapi":
		args = append(args,
			"-vf", "fps=30,format=nv12,hwupload",
			"-profile:v", "constrained_baseline",
			"-level", "4.2",
		)
	case "h264_nvenc":
		args = append(args,
			"-vf", "fps=30,format=yuv420p",
			"-preset", "fast",
			"-profile:v", "baseline",
			"-level", "4.2",
		)
	case "libx264":
		args = append(args,
			"-vf", "fps=30,format=yuv420p",
			"-preset", "veryfast",
			"-tune", "zerolatency",
			"-profile:v", "baseline",
			"-level", "4.2",
			"-x264-params", "slices=1:sliced-threads=0",
		)
	}
	args = append(args,
		"-bsf:v", "dump_extra",
		"-f", "h264",
		"pipe:1",
	)
	return args
}

// ffmpegHasPipeWire checks whether the installed ffmpeg has the pipewiregrab
// lavfi source. The `-h source=NAME` syntax was added in ffmpeg 5.0 — many
// distros (Fedora's ffmpeg-free, older ffmpeg builds) reject it outright, so
// we parse `-sources` / `-filters` instead.
func ffmpegHasPipeWire() bool {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, "ffmpeg", "-hide_banner", "-sources")
	if out, err := cmd.CombinedOutput(); err == nil && contains(out, []byte("pipewiregrab")) {
		return true
	}
	ctx2, cancel2 := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel2()
	cmd = exec.CommandContext(ctx2, "ffmpeg", "-hide_banner", "-filters")
	out, err := cmd.CombinedOutput()
	if err != nil {
		return false
	}
	return contains(out, []byte("pipewiregrab"))
}

func contains(haystack, needle []byte) bool {
	for i := 0; i+len(needle) <= len(haystack); i++ {
		match := true
		for j := 0; j < len(needle); j++ {
			if haystack[i+j] != needle[j] {
				match = false
				break
			}
		}
		if match {
			return true
		}
	}
	return false
}
