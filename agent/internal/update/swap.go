package update

import (
	"fmt"
	"os"
)

// SwapBinary renames currentPath → currentPath+".prev", then newPath → currentPath.
// On POSIX os.Rename is atomic within the same filesystem. On Windows the
// rename is done with MoveFileExW via the platform file.
func SwapBinary(newPath, currentPath string) error {
	prevPath := currentPath + ".prev"

	// remove stale .prev if present
	_ = os.Remove(prevPath)

	if err := rename(currentPath, prevPath); err != nil {
		return fmt.Errorf("backing up current binary: %w", err)
	}

	if err := rename(newPath, currentPath); err != nil {
		// try to restore
		_ = rename(prevPath, currentPath)
		return fmt.Errorf("installing new binary: %w", err)
	}
	return nil
}

// RollbackBinary undoes SwapBinary: renames currentPath+".prev" → currentPath.
func RollbackBinary(currentPath string) error {
	prevPath := currentPath + ".prev"
	if _, err := os.Stat(prevPath); os.IsNotExist(err) {
		return fmt.Errorf("no previous binary found at %s", prevPath)
	}
	_ = os.Remove(currentPath)
	if err := rename(prevPath, currentPath); err != nil {
		return fmt.Errorf("restoring previous binary: %w", err)
	}
	return nil
}
