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
	for _, s := range samples {
		types[s.Type] = true
		if s.Value < 0 || s.Value > 100 {
			t.Errorf("unexpected value for %s: %f", s.Type, s.Value)
		}
		if s.CollectedAt.IsZero() {
			t.Errorf("zero CollectedAt for %s", s.Type)
		}
	}

	for _, expected := range []string{"cpu", "ram", "disk"} {
		if !types[expected] {
			t.Errorf("missing metric type: %s", expected)
		}
	}
}
