package metrics

import (
	"testing"
)

func TestCollectReturnsSamples(t *testing.T) {
	samples, err := Collect()
	if err != nil {
		t.Fatalf("Collect() error: %v", err)
	}

	types := make(map[string]bool)
	hasCoreLabel := false
	hasDiskLabel := false
	hasNicLabel := false

	for _, s := range samples {
		types[s.Type] = true
		if s.CollectedAt.IsZero() {
			t.Errorf("zero CollectedAt for %s", s.Type)
		}
		if s.Type == "cpu" || s.Type == "ram" || s.Type == "disk" || s.Type == "cpu.core" {
			if s.Value < 0 || s.Value > 100 {
				t.Errorf("unexpected percent value for %s: %f", s.Type, s.Value)
			}
		}
		if s.Type == "cpu.core" && s.Labels["core"] != "" {
			hasCoreLabel = true
		}
		if s.Type == "disk.used_bytes" && s.Labels["mount"] != "" {
			hasDiskLabel = true
		}
		if s.Type == "net.rx_bytes" && s.Labels["nic"] != "" {
			hasNicLabel = true
		}
	}

	for _, expected := range []string{
		"cpu", "ram", "disk",
		"cpu.core",
		"ram.used_bytes", "ram.available_bytes", "ram.total_bytes",
		"swap.used_bytes", "swap.total_bytes",
		"disk.used_bytes", "disk.free_bytes", "disk.total_bytes",
		"net.rx_bytes", "net.tx_bytes", "net.rx_packets", "net.tx_packets",
	} {
		if !types[expected] {
			t.Errorf("missing metric type: %s", expected)
		}
	}

	if !hasCoreLabel {
		t.Errorf("expected cpu.core samples to carry a core label")
	}
	if !hasDiskLabel {
		t.Errorf("expected disk.used_bytes samples to carry a mount label")
	}
	if !hasNicLabel {
		t.Errorf("expected net.rx_bytes samples to carry a nic label")
	}
}
