package update

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"
)

const checkInterval = 30 * time.Minute

type attemptsRecord struct {
	Version  string    `json:"version"`
	Count    int       `json:"count"`
	LastSeen time.Time `json:"last_seen"`
}

// Updater holds the configuration needed to check, download, and apply updates.
type Updater struct {
	APIURL         string
	DataDir        string
	CurrentVersion string
	// RestartFn is called after a successful binary swap to hand control back
	// to the service manager. Typically calls os.Exit(0) or svc.Restart().
	RestartFn func()
}

func (u *Updater) attemptsPath() string {
	return filepath.Join(u.DataDir, "update_attempts.json")
}

func (u *Updater) loadAttempts(version string) attemptsRecord {
	data, err := os.ReadFile(u.attemptsPath())
	if err != nil {
		return attemptsRecord{Version: version}
	}
	var r attemptsRecord
	if err := json.Unmarshal(data, &r); err != nil || r.Version != version {
		return attemptsRecord{Version: version}
	}
	return r
}

func (u *Updater) recordAttempt(version string) {
	r := u.loadAttempts(version)
	r.Version = version
	r.Count++
	r.LastSeen = time.Now()
	data, _ := json.Marshal(r)
	_ = os.WriteFile(u.attemptsPath(), data, 0600)
}

// RunOnce performs a single update check and, if a newer version is available,
// downloads, verifies, swaps, and triggers a restart. Exported for tests.
func (u *Updater) RunOnce(ctx context.Context) error {
	info, err := Check(ctx, u.APIURL, u.CurrentVersion)
	if err != nil {
		return fmt.Errorf("update check: %w", err)
	}
	if info == nil {
		return nil // already up to date
	}

	// panic-loop guard: skip if this version has failed twice already
	attempts := u.loadAttempts(info.Version)
	if attempts.Count >= 2 {
		fmt.Fprintf(os.Stderr, "[update] skipping version %s: failed %d time(s) before\n",
			info.Version, attempts.Count)
		return nil
	}

	fmt.Printf("[update] new version available: %s (current: %s)\n", info.Version, u.CurrentVersion)

	binPath, err := currentBinaryPath()
	if err != nil {
		return fmt.Errorf("resolving binary path: %w", err)
	}

	newPath := binPath + ".new"
	fmt.Printf("[update] downloading %s ...\n", info.Version)
	if err := Download(ctx, info.DownloadURL, newPath, info.SHA256); err != nil {
		u.recordAttempt(info.Version)
		return fmt.Errorf("download: %w", err)
	}

	if err := SavePending(u.DataDir, u.CurrentVersion, info.Version); err != nil {
		_ = os.Remove(newPath)
		return fmt.Errorf("saving pending marker: %w", err)
	}

	if err := SwapBinary(newPath, binPath); err != nil {
		ClearPending(u.DataDir)
		u.recordAttempt(info.Version)
		return fmt.Errorf("swap: %w", err)
	}

	fmt.Printf("[update] binary swapped to %s; restarting\n", info.Version)
	u.RestartFn()
	return nil
}

// Start runs the update loop until ctx is cancelled. It checks immediately on
// startup (after a 10s delay so the agent is fully connected first), then
// every checkInterval.
func (u *Updater) Start(ctx context.Context) {
	select {
	case <-time.After(10 * time.Second):
	case <-ctx.Done():
		return
	}

	if err := u.RunOnce(ctx); err != nil {
		fmt.Fprintf(os.Stderr, "[update] check failed: %v\n", err)
	}

	ticker := time.NewTicker(checkInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ticker.C:
			if err := u.RunOnce(ctx); err != nil {
				fmt.Fprintf(os.Stderr, "[update] check failed: %v\n", err)
			}
		case <-ctx.Done():
			return
		}
	}
}

// currentBinaryPath returns os.Executable() cleaned of symlinks.
func currentBinaryPath() (string, error) {
	return os.Executable()
}
