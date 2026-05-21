//go:build windows

package capture

import (
	"context"
	"io"
	"os/exec"
	"strings"
	"time"
)

// StartAudio captures Windows system audio (loopback) and feeds an Opus
// track. Tries WASAPI loopback first — modern ffmpeg builds expose this
// as `-f wasapi -i loopback`. Falls back to the DirectShow
// "virtual-audio-capturer" device when WASAPI isn't available.
//
// Returns ErrAudioUnavailable if neither input works so the session can
// continue without audio.
func StartAudio(ctx context.Context, t Target) error {
	if _, err := exec.LookPath("ffmpeg"); err != nil {
		t.Logger.Println("audio: ffmpeg not found, skipping audio capture")
		return ErrAudioUnavailable
	}

	if ffmpegHasFormat("wasapi") {
		t.Logger.Println("audio: using WASAPI loopback")
		return runAudioPipeline(ctx, t, wasapiArgs())
	}
	if ffmpegHasDShowDevice("virtual-audio-capturer") {
		t.Logger.Println("audio: using DirectShow virtual-audio-capturer")
		return runAudioPipeline(ctx, t, dshowArgs("virtual-audio-capturer"))
	}

	t.Logger.Println("audio: no usable Windows audio input found")
	return ErrAudioUnavailable
}

func wasapiArgs() []string {
	return []string{
		"-hide_banner",
		"-loglevel", "warning",
		"-fflags", "+nobuffer",
		"-f", "wasapi",
		"-i", "loopback",
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
}

func dshowArgs(device string) []string {
	return []string{
		"-hide_banner",
		"-loglevel", "warning",
		"-fflags", "+nobuffer",
		"-f", "dshow",
		"-i", "audio=" + device,
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
}

// ffmpegHasFormat asks `ffmpeg -formats` and looks for the given name.
func ffmpegHasFormat(name string) bool {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	out, err := exec.CommandContext(ctx, "ffmpeg", "-hide_banner", "-formats").Output()
	if err != nil {
		return false
	}
	return strings.Contains(string(out), " "+name+" ") || strings.Contains(string(out), " "+name+",")
}

// ffmpegHasDShowDevice checks DirectShow audio device list for a name.
// Output goes to stderr and ffmpeg exits non-zero — that's expected.
func ffmpegHasDShowDevice(name string) bool {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, "ffmpeg", "-hide_banner", "-list_devices", "true", "-f", "dshow", "-i", "dummy")
	var buf strings.Builder
	cmd.Stderr = stderrSink{&buf}
	cmd.Stdout = io.Discard
	_ = cmd.Run()
	return strings.Contains(buf.String(), name)
}

type stderrSink struct{ b *strings.Builder }

func (s stderrSink) Write(p []byte) (int, error) {
	s.b.Write(p)
	return len(p), nil
}
