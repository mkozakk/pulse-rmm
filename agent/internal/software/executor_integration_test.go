//go:build linux
// +build linux

package software

import (
	"testing"
)

func TestExecuteInstall(t *testing.T) {
	// Execute apt-get install hello
	exitCode, output, err := Execute("install", "hello", "", "")
	if err != nil {
		t.Fatalf("Execute failed: %v", err)
	}

	if exitCode != 0 {
		t.Logf("Exit code: %d\nOutput: %s", exitCode, output)
		t.Fatalf("apt-get install failed with exit code %d", exitCode)
	}

	t.Logf("Successfully installed hello: %s", output)
}

func TestScanAfterInstall(t *testing.T) {
	// First scan
	items1, err := Scan()
	if err != nil {
		t.Fatalf("First scan failed: %v", err)
	}
	t.Logf("Scan 1: %d items", len(items1))

	// Install hello
	exitCode, output, err := Execute("install", "hello", "", "")
	if err != nil {
		t.Fatalf("Install failed: %v", err)
	}
	t.Logf("Install exit code: %d, output: %s", exitCode, output)

	// Second scan
	items2, err := Scan()
	if err != nil {
		t.Fatalf("Second scan failed: %v", err)
	}
	t.Logf("Scan 2: %d items", len(items2))

	// Check if hello is in the list
	found := false
	for _, item := range items2 {
		if item.Name == "hello" {
			found = true
			t.Logf("✓ Found hello in scan: version=%s", item.Version)
			break
		}
	}

	if !found {
		t.Logf("✗ hello NOT found in scan")
		t.Logf("Items: %v", items2)
		t.Fatalf("hello not found after install")
	}
}

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
