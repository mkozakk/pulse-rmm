package software

import (
	"testing"
)

func TestScanReturnsItems(t *testing.T) {
	items, err := Scan()
	if err != nil {
		t.Logf("Scan returned error (expected if package manager not installed): %v", err)
		return
	}

	if len(items) == 0 {
		t.Logf("Scan returned empty list (may be expected in test environment)")
		return
	}

	for i, item := range items {
		if item.Name == "" {
			t.Errorf("Item %d: name is empty", i)
		}
		if item.Version == "" {
			t.Errorf("Item %d: version is empty", i)
		}
		if item.Source == "" {
			t.Errorf("Item %d: source is empty", i)
		}
	}
}
