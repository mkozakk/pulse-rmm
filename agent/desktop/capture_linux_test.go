//go:build linux

package desktop

import (
	"os"
	"testing"

	"github.com/stretchr/testify/require"
)

func TestWaylandBlockedWhenWaylandDisplaySet(t *testing.T) {
	t.Setenv("WAYLAND_DISPLAY", ":0")
	err := checkPlatform()
	require.ErrorIs(t, err, ErrWaylandNotSupported)
}

func TestX11CaptureDetectedWhenDisplaySet(t *testing.T) {
	if os.Getenv("DISPLAY") == "" {
		t.Skip("no X11 display available")
	}
	t.Setenv("WAYLAND_DISPLAY", "")
	err := checkPlatform()
	require.NoError(t, err)
}
