//go:build !linux && !windows

package capture

import (
	"context"
	"fmt"
)

// Start returns an error on unsupported platforms.
func Start(ctx context.Context, t Target) error {
	return fmt.Errorf("screen capture not supported on this platform")
}
