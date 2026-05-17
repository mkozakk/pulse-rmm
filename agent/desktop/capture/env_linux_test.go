//go:build linux

package capture

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/require"
)

func TestIsWaylandSessionFromEnv(t *testing.T) {
	t.Setenv("XDG_SESSION_TYPE", "wayland")
	t.Setenv("WAYLAND_DISPLAY", "wayland-0")
	require.True(t, IsWaylandSession())
}

func TestIsWaylandSessionFromSocketProbe(t *testing.T) {
	dir := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(dir, "wayland-1"), nil, 0600))

	t.Setenv("XDG_RUNTIME_DIR", dir)
	t.Setenv("XDG_SESSION_TYPE", "")
	t.Setenv("WAYLAND_DISPLAY", "")

	require.True(t, IsWaylandSession())
	require.Equal(t, "wayland-1", os.Getenv("WAYLAND_DISPLAY"))
	require.Equal(t, "wayland", os.Getenv("XDG_SESSION_TYPE"))
}

func TestIsWaylandSessionFalseWhenNothingPresent(t *testing.T) {
	t.Setenv("XDG_RUNTIME_DIR", t.TempDir())
	t.Setenv("XDG_SESSION_TYPE", "")
	t.Setenv("WAYLAND_DISPLAY", "")
	require.False(t, IsWaylandSession())
}
