//go:build linux

package capture

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// detectWaylandSocket looks for $XDG_RUNTIME_DIR/wayland-N. xdg autostart
// doesn't always propagate WAYLAND_DISPLAY (varies by DE / launch mode), but
// the compositor always creates the socket — so disk is more reliable than env.
func detectWaylandSocket() string {
	dir := runtimeDir()
	matches, _ := filepath.Glob(filepath.Join(dir, "wayland-*"))
	for _, m := range matches {
		base := filepath.Base(m)
		if strings.HasSuffix(base, ".lock") {
			continue
		}
		return base
	}
	return ""
}

func runtimeDir() string {
	if d := os.Getenv("XDG_RUNTIME_DIR"); d != "" {
		return d
	}
	return fmt.Sprintf("/run/user/%d", os.Getuid())
}

// IsWaylandSession returns true if we're inside a Wayland session. Env first,
// then a socket probe. When the probe finds a socket but env is unset, it
// backfills WAYLAND_DISPLAY, XDG_SESSION_TYPE, and DBUS_SESSION_BUS_ADDRESS
// so child processes (ffmpeg pipewiregrab, the portal client) inherit
// working values.
func IsWaylandSession() bool {
	if os.Getenv("XDG_SESSION_TYPE") == "wayland" || os.Getenv("WAYLAND_DISPLAY") != "" {
		ensureSessionBus()
		return true
	}
	if sock := detectWaylandSocket(); sock != "" {
		_ = os.Setenv("WAYLAND_DISPLAY", sock)
		if os.Getenv("XDG_SESSION_TYPE") == "" {
			_ = os.Setenv("XDG_SESSION_TYPE", "wayland")
		}
		ensureSessionBus()
		return true
	}
	return false
}

// ensureSessionBus backfills DBUS_SESSION_BUS_ADDRESS from the standard
// systemd user bus location when missing. xdg-desktop-portal calls fail
// without it.
func ensureSessionBus() {
	if os.Getenv("DBUS_SESSION_BUS_ADDRESS") != "" {
		return
	}
	busPath := filepath.Join(runtimeDir(), "bus")
	if _, err := os.Stat(busPath); err == nil {
		_ = os.Setenv("DBUS_SESSION_BUS_ADDRESS", "unix:path="+busPath)
	}
}

// DescribeSessionEnv returns a single-line summary of the env signals that
// drive capture/input dispatch. Logged at helper startup and session start.
func DescribeSessionEnv() string {
	return fmt.Sprintf(
		"XDG_SESSION_TYPE=%q WAYLAND_DISPLAY=%q DISPLAY=%q DBUS_SESSION_BUS_ADDRESS=%q XDG_RUNTIME_DIR=%q wayland_socket=%q",
		os.Getenv("XDG_SESSION_TYPE"),
		os.Getenv("WAYLAND_DISPLAY"),
		os.Getenv("DISPLAY"),
		os.Getenv("DBUS_SESSION_BUS_ADDRESS"),
		os.Getenv("XDG_RUNTIME_DIR"),
		detectWaylandSocket(),
	)
}
