//go:build linux

package capture

import (
	"context"
	"io"
	"os/exec"
	"strings"
	"time"
)

// StartAudio captures system audio on Linux and emits an Opus track on
// the peer connection. It works on both X11 and Wayland because both
// modern desktops expose a PulseAudio-compatible monitor source — on
// Wayland this is served by pipewire-pulse.
//
// If ffmpeg or PulseAudio aren't available, returns ErrAudioUnavailable so
// the caller can keep the session video-only.
func StartAudio(ctx context.Context, t Target) error {
	if _, err := exec.LookPath("ffmpeg"); err != nil {
		t.Logger.Println("audio: ffmpeg not found, skipping audio capture")
		return ErrAudioUnavailable
	}

	source := detectPulseMonitorSource(t.Logger.Printf)
	if source == "" {
		t.Logger.Println("audio: no PulseAudio monitor source found, skipping audio capture")
		return ErrAudioUnavailable
	}
	t.Logger.Printf("audio: using PulseAudio source %q", source)

	args := []string{
		"-hide_banner",
		"-loglevel", "warning",
		"-fflags", "+nobuffer",
		"-f", "pulse",
		"-i", source,
		"-ac", "2",
		"-ar", "48000",
		"-c:a", "libopus",
		"-b:a", "96k",
		"-application", "lowdelay",
		"-frame_duration", "20",
		"-vbr", "on",
		"-f", "ogg",
		"pipe:1",
	}

	return runAudioPipeline(ctx, t, args)
}

// detectPulseMonitorSource asks `pactl` for the default sink's monitor
// source. Falls back to a bare "default" string (pulse will pick the
// default capture device, which is usually but not always the monitor).
func detectPulseMonitorSource(log logf) string {
	if _, err := exec.LookPath("pactl"); err == nil {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		out, err := exec.CommandContext(ctx, "pactl", "get-default-sink").Output()
		if err == nil {
			sink := strings.TrimSpace(string(out))
			if sink != "" {
				return sink + ".monitor"
			}
		}
		log("audio: pactl get-default-sink failed: %v", err)
	}
	// pulse server reachable? If not, refuse — ffmpeg will hang otherwise.
	if !pulseReachable() {
		return ""
	}
	return "default"
}

// pulseReachable does a quick `pactl info` to confirm a Pulse-compatible
// server is up. Two-second budget so we don't block session startup.
func pulseReachable() bool {
	if _, err := exec.LookPath("pactl"); err != nil {
		return false
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, "pactl", "info")
	cmd.Stdout = io.Discard
	cmd.Stderr = io.Discard
	return cmd.Run() == nil
}
