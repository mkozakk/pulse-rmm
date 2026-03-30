package sysinfo

import (
	"testing"
)

func TestCollectReturnsSystemInfo(t *testing.T) {
	info, err := Collect()
	if err != nil {
		t.Fatalf("Collect() error: %v", err)
	}

	if info.CPU.LogicalCores <= 0 {
		t.Errorf("expected positive logical cores, got %d", info.CPU.LogicalCores)
	}
	if info.Memory.TotalBytes == 0 {
		t.Errorf("expected non-zero total memory")
	}
	if len(info.Disks) == 0 {
		t.Errorf("expected at least one disk")
	}
	if len(info.Nics) == 0 {
		t.Errorf("expected at least one nic (loopback always present)")
	}
	if info.CollectedAt.IsZero() {
		t.Errorf("expected CollectedAt to be set")
	}
}
