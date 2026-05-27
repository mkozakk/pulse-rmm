package processes

import (
	"os"
	"testing"
)

func TestListIncludesCurrentProcess(t *testing.T) {
	procs, err := List()
	if err != nil {
		t.Fatalf("List() returned error: %v", err)
	}
	if len(procs) == 0 {
		t.Fatalf("expected at least one process, got 0")
	}

	self := int32(os.Getpid())
	var found *Info
	for i := range procs {
		if procs[i].PID == self {
			found = &procs[i]
			break
		}
	}
	if found == nil {
		t.Fatalf("expected current test process (pid %d) in list", self)
	}
	if found.Name == "" {
		t.Errorf("expected non-empty process name for pid %d", self)
	}
}
