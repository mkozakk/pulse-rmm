package software

import (
	"testing"
)

func TestExecuteWithUnknownAction(t *testing.T) {
	exitCode, output, err := Execute("unknown", "test", "1.0", "")
	if err == nil {
		t.Error("Expected error for unknown action, got nil")
	}
	if exitCode != -1 {
		t.Errorf("Expected exit code -1, got %d", exitCode)
	}
	if output != "" {
		t.Errorf("Expected empty output, got %q", output)
	}
}

func TestExecuteInstallMock(t *testing.T) {
	// This test will fail in environments without package managers
	// It's just a smoke test to ensure the function is callable
	_, _, _ = Execute("install", "nonexistent-package-xyz", "", "")
}

func TestExecuteRemove(t *testing.T) {
	// This test will fail in environments without package managers
	// It's just a smoke test to ensure the function is callable
	_, _, _ = Execute("remove", "nonexistent-package-xyz", "", "")
}
