package svc

import (
	"fmt"

	"golang.org/x/sys/windows"
)

func checkPrivilege() error {
	token, err := windows.OpenCurrentProcessToken()
	if err != nil {
		return fmt.Errorf("checking privileges: %w", err)
	}
	defer token.Close()

	elevated := token.IsElevated()
	if !elevated {
		return fmt.Errorf("must be run as Administrator (right-click → Run as administrator)")
	}
	return nil
}
