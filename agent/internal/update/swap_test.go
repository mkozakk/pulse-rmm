package update

import (
	"os"
	"path/filepath"
	"testing"
)

func TestSwapBinary(t *testing.T) {
	dir := t.TempDir()
	current := filepath.Join(dir, "agent")
	newBin := filepath.Join(dir, "agent.new")

	os.WriteFile(current, []byte("old"), 0755)
	os.WriteFile(newBin, []byte("new"), 0755)

	if err := SwapBinary(newBin, current); err != nil {
		t.Fatalf("SwapBinary failed: %v", err)
	}

	data, _ := os.ReadFile(current)
	if string(data) != "new" {
		t.Errorf("current binary = %q, want %q", data, "new")
	}
	data, _ = os.ReadFile(current + ".prev")
	if string(data) != "old" {
		t.Errorf(".prev binary = %q, want %q", data, "old")
	}
	if _, err := os.Stat(newBin); !os.IsNotExist(err) {
		t.Error(".new should be gone after swap")
	}
}

func TestRollbackBinary(t *testing.T) {
	dir := t.TempDir()
	current := filepath.Join(dir, "agent")
	prev := current + ".prev"

	os.WriteFile(current, []byte("bad"), 0755)
	os.WriteFile(prev, []byte("good"), 0755)

	if err := RollbackBinary(current); err != nil {
		t.Fatalf("RollbackBinary failed: %v", err)
	}

	data, _ := os.ReadFile(current)
	if string(data) != "good" {
		t.Errorf("after rollback binary = %q, want %q", data, "good")
	}
}

func TestRollback_missingPrev(t *testing.T) {
	dir := t.TempDir()
	current := filepath.Join(dir, "agent")
	os.WriteFile(current, []byte("bin"), 0755)

	if err := RollbackBinary(current); err == nil {
		t.Error("expected error when .prev does not exist")
	}
}
