//go:build !windows && !linux

package desktop

// RunHelper is a no-op on non-Windows platforms — sessions run in-process.
func RunHelper(addr string) {}
