//go:build !windows

package svc

import (
	"fmt"
	"os"
)

func checkPrivilege() error {
	if os.Geteuid() != 0 {
		return fmt.Errorf("must be run as root (use sudo)")
	}
	return nil
}
