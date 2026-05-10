//go:build linux

package desktop

import (
	"fmt"

	"github.com/jezek/xgb"
	"github.com/jezek/xgb/xproto"
	"github.com/jezek/xgb/xtest"
)

type x11Injector struct {
	conn   *xgb.Conn
	root   xproto.Window
}

func newInputInjector() (InputInjector, error) {
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
	return xtest.FakeInputChecked(x.conn,
		evType, byte(button+1), 0,
		x.root, 0, 0, 0,
	).Check()
}

func (x *x11Injector) MouseScroll(deltaX, deltaY int) error {
	// X11 scroll: button 4=up, 5=down, 6=left, 7=right. Each press = one notch.
	press := func(btn byte) error {
		if err := xtest.FakeInputChecked(x.conn, xproto.ButtonPress, btn, 0, x.root, 0, 0, 0).Check(); err != nil {
			return err
		}
		return xtest.FakeInputChecked(x.conn, xproto.ButtonRelease, btn, 0, x.root, 0, 0, 0).Check()
	}
	if deltaY < 0 {
		return press(4) // scroll up
	} else if deltaY > 0 {
		return press(5) // scroll down
	}
	if deltaX < 0 {
		return press(6) // scroll left
	} else if deltaX > 0 {
		return press(7) // scroll right
	}
	return nil
}

func (x *x11Injector) KeyEvent(keyCode int, pressed bool) error {
	evType := byte(xproto.KeyPress)
	if !pressed {
		evType = xproto.KeyRelease
	}
	return xtest.FakeInputChecked(x.conn,
		evType, byte(keyCode), 0,
		x.root, 0, 0, 0,
	).Check()
}

func (x *x11Injector) Close() error {
	x.conn.Close()
	return nil
}
