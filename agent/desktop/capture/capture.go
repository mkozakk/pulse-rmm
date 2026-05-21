package capture

import (
	"errors"
	"log"
	"os"

	"github.com/pion/webrtc/v4"
)

// Target contains what the capture pipeline needs from a desktop session.
// Callers in package desktop fill this from DesktopSession private fields and
// pass it down so capture can stay in its own package without importing desktop.
type Target struct {
	Logger        *log.Logger
	LogFile       *os.File
	AddTrack      func(webrtc.TrackLocal) error
	AddAudioTrack func(webrtc.TrackLocal) error
}

// Error sentinels for portal/capture failures. The strings are stable codes
// the webapp switches on; do not rename without updating the UI.
var (
	ErrPortalNotInstalled = errors.New("wayland_portal_missing")
	ErrConsentDenied      = errors.New("wayland_consent_denied")
	ErrConsentTimeout     = errors.New("wayland_consent_timeout")
	ErrPortalNoStream     = errors.New("wayland_no_stream")
	ErrFFmpegNoPipeWire   = errors.New("wayland_ffmpeg_no_pipewire")
)
