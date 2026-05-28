//go:build windows

package input

import (
	"fmt"
	"syscall"
	"unsafe"
)

var (
	user32           = syscall.NewLazyDLL("user32.dll")
	sendInput        = user32.NewProc("SendInput")
	getSystemMetrics = user32.NewProc("GetSystemMetrics")
)

const (
	inputMouse    = 0
	inputKeyboard = 1

	mouseMoveAbsolute = 0x8001 // MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE
	mouseLeftDown     = 0x0002
	mouseLeftUp       = 0x0004
	mouseRightDown    = 0x0008
	mouseRightUp      = 0x0010
	mouseMiddleDown   = 0x0020
	mouseMiddleUp     = 0x0040
	mouseWheel        = 0x0800 // MOUSEEVENTF_WHEEL (vertical)
	mouseHWheel       = 0x1000 // MOUSEEVENTF_HWHEEL (horizontal)

	keyEventKeyUp = 0x0002

	smCxScreen = 0
	smCyScreen = 1
)

type mouseInput struct {
	dx, dy      int32
	mouseData   uint32
	dwFlags     uint32
	time        uint32
	_           [4]byte // align dwExtraInfo to 8 bytes (matches MOUSEINPUT layout on 64-bit)
	dwExtraInfo uintptr
}

type keybdInput struct {
	wVk         uint16
	wScan       uint16
	dwFlags     uint32
	time        uint32
	_           [4]byte // align dwExtraInfo to 8 bytes (matches KEYBDINPUT layout on 64-bit)
	dwExtraInfo uintptr
}

// input mirrors the Windows INPUT struct (40 bytes on 64-bit).
// data holds the union: MOUSEINPUT=32 bytes, KEYBDINPUT=24 bytes.
type input struct {
	inputType uint32
	_         [4]byte  // pad to align union to 8-byte boundary
	data      [32]byte // union max size (MOUSEINPUT on 64-bit)
}

type windowsInjector struct{}

// New returns a Windows SendInput-based injector.
func New() (InputInjector, error) {
	return &windowsInjector{}, nil
}

func (w *windowsInjector) MouseMove(x, y int) error {
	screenW, _, _ := getSystemMetrics.Call(smCxScreen)
	screenH, _, _ := getSystemMetrics.Call(smCyScreen)
	if screenW == 0 || screenH == 0 {
		return fmt.Errorf("could not get screen dimensions")
	}
	// normalize to 0-65535 range for MOUSEEVENTF_ABSOLUTE
	normX := int32(x * 65535 / int(screenW))
	normY := int32(y * 65535 / int(screenH))

	mi := mouseInput{dx: normX, dy: normY, dwFlags: mouseMoveAbsolute}
	in := input{inputType: inputMouse}
	*(*mouseInput)(unsafe.Pointer(&in.data)) = mi
	ret, _, err := sendInput.Call(1, uintptr(unsafe.Pointer(&in)), unsafe.Sizeof(in))
	if ret == 0 {
		return fmt.Errorf("SendInput: %w", err)
	}
	return nil
}

func (w *windowsInjector) MouseButton(button int, pressed bool) error {
	var flags uint32
	switch button {
	case 0:
		if pressed {
			flags = mouseLeftDown
		} else {
			flags = mouseLeftUp
		}
	case 1:
		if pressed {
			flags = mouseRightDown
		} else {
			flags = mouseRightUp
		}
	case 2:
		if pressed {
			flags = mouseMiddleDown
		} else {
			flags = mouseMiddleUp
		}
	default:
		return nil
	}
	mi := mouseInput{dwFlags: flags}
	in := input{inputType: inputMouse}
	*(*mouseInput)(unsafe.Pointer(&in.data)) = mi
	ret, _, err := sendInput.Call(1, uintptr(unsafe.Pointer(&in)), unsafe.Sizeof(in))
	if ret == 0 {
		return fmt.Errorf("SendInput: %w", err)
	}
	return nil
}

func (w *windowsInjector) MouseScroll(deltaX, deltaY int) error {
	// JS deltaY: positive = scroll down. Windows WHEEL: positive mouseData = scroll up. Invert.
	// JS gives pixel deltas; map roughly to Windows WHEEL_DELTA units (120 per notch).
	if deltaY != 0 {
		mi := mouseInput{mouseData: uint32(int32(-deltaY)), dwFlags: mouseWheel}
		in := input{inputType: inputMouse}
		*(*mouseInput)(unsafe.Pointer(&in.data)) = mi
		ret, _, err := sendInput.Call(1, uintptr(unsafe.Pointer(&in)), unsafe.Sizeof(in))
		if ret == 0 {
			return fmt.Errorf("SendInput wheel: %w", err)
		}
	}
	if deltaX != 0 {
		mi := mouseInput{mouseData: uint32(int32(-deltaX)), dwFlags: mouseHWheel}
		in := input{inputType: inputMouse}
		*(*mouseInput)(unsafe.Pointer(&in.data)) = mi
		ret, _, err := sendInput.Call(1, uintptr(unsafe.Pointer(&in)), unsafe.Sizeof(in))
		if ret == 0 {
			return fmt.Errorf("SendInput hwheel: %w", err)
		}
	}
	return nil
}

func (w *windowsInjector) KeyEvent(keyCode int, pressed bool) error {
	var flags uint32
	if !pressed {
		flags = keyEventKeyUp
	}
	ki := keybdInput{wVk: uint16(keyCode), dwFlags: flags}
	in := input{inputType: inputKeyboard}
	*(*keybdInput)(unsafe.Pointer(&in.data)) = ki
	ret, _, err := sendInput.Call(1, uintptr(unsafe.Pointer(&in)), unsafe.Sizeof(in))
	if ret == 0 {
		return fmt.Errorf("SendInput: %w", err)
	}
	return nil
}

func (w *windowsInjector) SetScreenSize(_, _ int) {}
func (w *windowsInjector) Close() error            { return nil }
