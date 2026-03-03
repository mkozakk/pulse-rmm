//go:build !windows

package svc

import (
	"os"
	"testing"
)

func TestCheckPrivilege_notRoot(t *testing.T) {
	if os.Geteuid() == 0 {
		t.Skip("test must run as non-root")
	}
	err := checkPrivilege()
	if err == nil {
		t.Fatal("expected error when not root")
	}
}
