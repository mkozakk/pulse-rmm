//go:build linux

package input

import (
	"fmt"
	"os"
	"os/exec"
	"regexp"
	"strconv"

	"github.com/bendahl/uinput"
	"github.com/jezek/xgb"
	"github.com/jezek/xgb/xproto"
	"github.com/jezek/xgb/xtest"
)

// domToX11 maps browser DOM keyCodes to X11 keycodes on a standard PC keyboard.
// Browser DOM keyCodes match Windows VK codes; X11 keycodes are Linux evdev scan codes + 8.
// Passing DOM keyCodes directly as X11 keycodes injects completely wrong keys (e.g. 'A'=65
// would hit X11 keycode 65 = Space).
var domToX11 = map[int]byte{
	8: 22, 9: 23, 13: 36, 16: 50, 17: 37, 18: 64, 20: 66, 27: 9,
	32: 65, 33: 112, 34: 117, 35: 115, 36: 110, 37: 113, 38: 111, 39: 114, 40: 116,
	45: 118, 46: 119,
	// digits 0–9
	48: 19, 49: 10, 50: 11, 51: 12, 52: 13, 53: 14, 54: 15, 55: 16, 56: 17, 57: 18,
	// letters A–Z
	65: 38, 66: 56, 67: 54, 68: 40, 69: 26, 70: 41, 71: 42, 72: 43, 73: 31, 74: 44,
	75: 45, 76: 46, 77: 58, 78: 57, 79: 32, 80: 33, 81: 24, 82: 27, 83: 39, 84: 28,
	85: 30, 86: 55, 87: 25, 88: 53, 89: 29, 90: 52,
	// Win/Menu
	91: 133, 93: 135,
	// numpad 0–9, *, +, Enter, -, ., /
	96: 90, 97: 87, 98: 88, 99: 89, 100: 83, 101: 84, 102: 85, 103: 79, 104: 80, 105: 81,
	106: 63, 107: 86, 108: 104, 109: 82, 110: 91, 111: 106,
	// F1–F12
	112: 67, 113: 68, 114: 69, 115: 70, 116: 71, 117: 72,
	118: 73, 119: 74, 120: 75, 121: 76, 122: 95, 123: 96,
	// NumLock, ScrollLock
	144: 77, 145: 78,
	// punctuation ; = , - . / ` [ \ ] '
	186: 47, 187: 21, 188: 59, 189: 20, 190: 60, 191: 61, 192: 49,
	219: 34, 220: 51, 221: 35, 222: 48,
}

// domToEvdev converts a DOM keyCode to a Linux evdev key code (X11 keycode − 8).
func domToEvdev(keyCode int) (int, bool) {
	x11, ok := domToX11[keyCode]
	if !ok || x11 < 8 {
		return 0, false
	}
	return int(x11) - 8, true
}

// --- X11 injector ---

type x11Injector struct {
	conn *xgb.Conn
	root xproto.Window
}

func newX11Injector() (InputInjector, error) {
	conn, err := xgb.NewConn()
	if err != nil {
		return nil, fmt.Errorf("connecting to X11: %w", err)
	}
	if err := xtest.Init(conn); err != nil {
		conn.Close()
		return nil, fmt.Errorf("initialising XTest: %w", err)
	}
	root := xproto.Setup(conn).DefaultScreen(conn).Root
	return &x11Injector{conn: conn, root: root}, nil
}

func (x *x11Injector) MouseMove(px, py int) error {
	return xtest.FakeInputChecked(x.conn,
		xproto.MotionNotify, 0, 0,
		x.root, int16(px), int16(py), 0,
	).Check()
}

func (x *x11Injector) MouseButton(button int, pressed bool) error {
	evType := byte(xproto.ButtonPress)
	if !pressed {
		evType = xproto.ButtonRelease
	}
	// X11 buttons: 1=left, 2=middle, 3=right; browser: 0=left, 1=middle, 2=right
	return xtest.FakeInputChecked(x.conn,
		evType, byte(button+1), 0,
		x.root, 0, 0, 0,
	).Check()
}

func (x *x11Injector) MouseScroll(deltaX, deltaY int) error {
	press := func(btn byte) error {
		if err := xtest.FakeInputChecked(x.conn, xproto.ButtonPress, btn, 0, x.root, 0, 0, 0).Check(); err != nil {
			return err
		}
		return xtest.FakeInputChecked(x.conn, xproto.ButtonRelease, btn, 0, x.root, 0, 0, 0).Check()
	}
	if deltaY < 0 {
		return press(4)
	} else if deltaY > 0 {
		return press(5)
	}
	if deltaX < 0 {
		return press(6)
	} else if deltaX > 0 {
		return press(7)
	}
	return nil
}

func (x *x11Injector) KeyEvent(keyCode int, pressed bool) error {
	x11Code, ok := domToX11[keyCode]
	if !ok {
		return nil
	}
	evType := byte(xproto.KeyPress)
	if !pressed {
		evType = xproto.KeyRelease
	}
	return xtest.FakeInputChecked(x.conn,
		evType, x11Code, 0,
		x.root, 0, 0, 0,
	).Check()
}

func (x *x11Injector) Close() error {
	x.conn.Close()
	return nil
}

// --- uinput injector (Wayland) ---
// Requires the agent process to be in the `input` group (for /dev/uinput access)
// and `video` group (for kmsgrab screen capture).

type uinputInjector struct {
	kb       uinput.Keyboard
	mouse    uinput.Mouse
	touchpad uinput.TouchPad
	screenW  int32
	screenH  int32
}

func newUinputInjector() (InputInjector, error) {
	if _, err := os.Stat("/dev/uinput"); err != nil {
		return nil, fmt.Errorf("/dev/uinput missing — kernel uinput module not loaded (run: sudo modprobe uinput): %w", err)
	}
	kb, err := uinput.CreateKeyboard("/dev/uinput", []byte("Pulse RMM Keyboard"))
	if err != nil {
		return nil, fmt.Errorf("opening /dev/uinput failed — user must be in 'input' group; re-login after install (current groups inherited at session start): %w", err)
	}
	mouse, err := uinput.CreateMouse("/dev/uinput", []byte("Pulse RMM Mouse"))
	if err != nil {
		kb.Close()
		return nil, fmt.Errorf("creating uinput mouse: %w", err)
	}
	w, h := detectScreenSize()
	tp, err := uinput.CreateTouchPad("/dev/uinput", []byte("Pulse RMM Pointer"), 0, w-1, 0, h-1)
	if err != nil {
		mouse.Close()
		kb.Close()
		return nil, fmt.Errorf("creating uinput touchpad: %w", err)
	}
	return &uinputInjector{kb: kb, mouse: mouse, touchpad: tp, screenW: w, screenH: h}, nil
}

// detectScreenSize asks XWayland (always present on Wayland sessions) for the
// primary display geometry — webapp coords are in capture-space pixels, and
// the absolute touchpad needs a range that matches so movement is 1:1 with
// the host cursor. Falls back to 1920x1080 if xrandr is unavailable.
func detectScreenSize() (int32, int32) {
	out, err := exec.Command("xrandr", "--query").Output()
	if err == nil {
		re := regexp.MustCompile(`(?m)^\s+(\d+)x(\d+)\s+[\d.]+\*`)
		if m := re.FindStringSubmatch(string(out)); len(m) == 3 {
			w, _ := strconv.Atoi(m[1])
			h, _ := strconv.Atoi(m[2])
			if w > 0 && h > 0 {
				return int32(w), int32(h)
			}
		}
	}
	return 1920, 1080
}

func (u *uinputInjector) MouseMove(x, y int) error {
	cx := clampInt32(int32(x), 0, u.screenW-1)
	cy := clampInt32(int32(y), 0, u.screenH-1)
	return u.touchpad.MoveTo(cx, cy)
}

func clampInt32(v, lo, hi int32) int32 {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}

func (u *uinputInjector) MouseButton(button int, pressed bool) error {
	// browser: 0=left, 1=middle, 2=right. Left/right go through the touchpad
	// so the click lands at the absolute position we just warped to; middle
	// falls back to the relative mouse device (libinput merges them).
	switch button {
	case 0:
		if pressed {
			return u.touchpad.LeftPress()
		}
		return u.touchpad.LeftRelease()
	case 1:
		if pressed {
			return u.mouse.MiddlePress()
		}
		return u.mouse.MiddleRelease()
	case 2:
		if pressed {
			return u.touchpad.RightPress()
		}
		return u.touchpad.RightRelease()
	}
	return nil
}

func (u *uinputInjector) MouseScroll(deltaX, deltaY int) error {
	if deltaY != 0 {
		// browser deltaY > 0 = scroll down; REL_WHEEL: positive = up, negative = down
		delta := int32(1)
		if deltaY > 0 {
			delta = -1
		}
		return u.mouse.Wheel(false, delta) // false = vertical (not horizontal)
	}
	if deltaX != 0 {
		delta := int32(1)
		if deltaX > 0 {
			delta = -1
		}
		return u.mouse.Wheel(true, delta) // true = horizontal
	}
	return nil
}

func (u *uinputInjector) KeyEvent(keyCode int, pressed bool) error {
	evdev, ok := domToEvdev(keyCode)
	if !ok {
		return nil
	}
	if pressed {
		return u.kb.KeyDown(evdev)
	}
	return u.kb.KeyUp(evdev)
}

func (u *uinputInjector) Close() error {
	err1 := u.touchpad.Close()
	err2 := u.mouse.Close()
	err3 := u.kb.Close()
	if err1 != nil {
		return err1
	}
	if err2 != nil {
		return err2
	}
	return err3
}

// --- factory ---

// New returns the best InputInjector for the current environment.
// On Wayland sessions it uses uinput (kernel evdev layer); on X11 it uses
// XTest; falls back to uinput when no display is detected (system service).
func New() (InputInjector, error) {
	// Wayland check comes first: XWayland is running under Wayland sessions so
	// DISPLAY is always set, but XTest input lands on XWayland only and won't
	// reach native Wayland clients. uinput injects at the kernel evdev layer
	// (libinput → compositor) and reaches every app.
	if os.Getenv("XDG_SESSION_TYPE") == "wayland" || os.Getenv("WAYLAND_DISPLAY") != "" {
		return newUinputInjector()
	}
	if os.Getenv("DISPLAY") != "" {
		return newX11Injector()
	}
	// No graphical session — system service running kmsgrab. uinput works
	// without a compositor; requires the running user to be in 'input' group.
	return newUinputInjector()
}
