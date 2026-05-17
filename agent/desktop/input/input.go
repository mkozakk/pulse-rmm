package input

// InputInjector is the interface for injecting mouse and keyboard events
// into the host operating system.
type InputInjector interface {
	MouseMove(x, y int) error
	MouseButton(button int, pressed bool) error
	MouseScroll(deltaX, deltaY int) error
	KeyEvent(keyCode int, pressed bool) error
	Close() error
}

// Noop returns an InputInjector that silently discards all events.
func Noop() InputInjector { return &noopInjector{} }

type noopInjector struct{}

func (n *noopInjector) MouseMove(x, y int) error                  { return nil }
func (n *noopInjector) MouseButton(button int, pressed bool) error { return nil }
func (n *noopInjector) MouseScroll(deltaX, deltaY int) error       { return nil }
func (n *noopInjector) KeyEvent(keyCode int, pressed bool) error   { return nil }
func (n *noopInjector) Close() error                               { return nil }
