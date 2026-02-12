//go:build !linux && !windows

package desktop

func newInputInjector() (InputInjector, error) {
	return &noopInjector{}, nil
}
