//go:build linux

package script

import (
	"testing"
)

func TestExecuteEchoScript(t *testing.T) {
	exitCode, output, err := Execute(`echo "hello"`, nil)
	if err != nil {
		t.Fatalf("expected no error, got: %v", err)
	}
	if exitCode != 0 {
		t.Fatalf("expected exit code 0, got: %d", exitCode)
	}
	if len(output) == 0 || !contains(output, "hello") {
		t.Fatalf("expected output to contain 'hello', got: %s", output)
	}
}

func TestExecuteWithEnvVars(t *testing.T) {
	script := `echo "$TEST_VAR"`
	envVars := map[string]string{"TEST_VAR": "world"}
	exitCode, output, err := Execute(script, envVars)
	if err != nil {
		t.Fatalf("expected no error, got: %v", err)
	}
	if exitCode != 0 {
		t.Fatalf("expected exit code 0, got: %d", exitCode)
	}
	if !contains(output, "world") {
		t.Fatalf("expected output to contain 'world', got: %s", output)
	}
}

func TestExecuteNonZeroExit(t *testing.T) {
	exitCode, _, err := Execute("exit 42", nil)
	if err != nil {
		t.Fatalf("expected no error, got: %v", err)
	}
	if exitCode != 42 {
		t.Fatalf("expected exit code 42, got: %d", exitCode)
	}
}

func contains(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}
