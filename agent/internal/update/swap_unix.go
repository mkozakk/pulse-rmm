//go:build !windows

package update

import "os"

func rename(src, dst string) error {
	return os.Rename(src, dst)
}
