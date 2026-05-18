//go:build linux
// +build linux

package software

import (
	"testing"
)

func TestRemovePackage(t *testing.T) {
	// Remove hello
	exitCode, output, err := Execute("remove", "hello", "", "")
	if err != nil {
		t.Fatalf("Remove failed: %v", err)
	}

	t.Logf("Remove exit code: %d, output: %s", exitCode, output)

	// Scan
	items, err := Scan()
	if err != nil {
		t.Fatalf("Scan failed: %v", err)
	}

	// Check if hello is gone
	found := false
	for _, item := range items {
		if item.Name == "hello" {
			found = true
			break
		}
	}

	if found {
		t.Fatalf("hello still found after remove")
	}
	t.Logf("✓ hello removed successfully")
}
