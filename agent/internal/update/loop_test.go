package update

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func TestRunOnce_upToDate(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]any{"upToDate": true})
	}))
	defer srv.Close()

	restartCalled := false
	u := &Updater{
		APIURL:         srv.URL,
		DataDir:        t.TempDir(),
		CurrentVersion: "1.0.0",
		RestartFn:      func() { restartCalled = true },
	}

	if err := u.RunOnce(context.Background()); err != nil {
		t.Fatalf("RunOnce returned error: %v", err)
	}
	if restartCalled {
		t.Error("restart should not be called when already up to date")
	}
}

func TestRunOnce_panicLoopGuard(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]any{
			"upToDate":    false,
			"version":     "2.0.0",
			"downloadUrl": "http://example.com/bad",
			"sha256":      "aaaa",
			"sizeBytes":   100,
		})
	}))
	defer srv.Close()

	dataDir := t.TempDir()
	u := &Updater{
		APIURL:         srv.URL,
		DataDir:        dataDir,
		CurrentVersion: "1.0.0",
		RestartFn:      func() {},
	}

	// record 2 prior failures for this version
	rec := attemptsRecord{Version: "2.0.0", Count: 2}
	data, _ := json.Marshal(rec)
	os.WriteFile(filepath.Join(dataDir, "update_attempts.json"), data, 0600)

	// RunOnce should skip the update without calling restart or downloading
	if err := u.RunOnce(context.Background()); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	// no pending file should have been written
	if _, err := os.Stat(filepath.Join(dataDir, "update_pending.json")); !os.IsNotExist(err) {
		t.Error("pending file should not exist when panic-loop guard fires")
	}
}

func TestSaveClearPending(t *testing.T) {
	dataDir := t.TempDir()

	if err := SavePending(dataDir, "1.0.0", "2.0.0"); err != nil {
		t.Fatalf("SavePending: %v", err)
	}

	p, err := LoadPending(dataDir)
	if err != nil {
		t.Fatalf("LoadPending: %v", err)
	}
	if p == nil {
		t.Fatal("expected non-nil pending")
	}
	if p.FromVersion != "1.0.0" || p.ToVersion != "2.0.0" {
		t.Errorf("pending = %+v", p)
	}

	ClearPending(dataDir)

	p2, _ := LoadPending(dataDir)
	if p2 != nil {
		t.Error("pending should be nil after clear")
	}
}
