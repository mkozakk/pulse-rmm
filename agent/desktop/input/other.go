//go:build !linux && !windows

package input

// New returns a no-op injector on unsupported platforms.
func New() (InputInjector, error) {
	return Noop(), nil
}
