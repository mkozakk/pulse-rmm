package update

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"
)

type pendingUpdate struct {
	FromVersion string    `json:"from_version"`
	ToVersion   string    `json:"to_version"`
	StartedAt   time.Time `json:"started_at"`
}

func pendingPath(dataDir string) string {
	return filepath.Join(dataDir, "update_pending.json")
}

func SavePending(dataDir, fromVersion, toVersion string) error {
	p := pendingUpdate{
		FromVersion: fromVersion,
		ToVersion:   toVersion,
		StartedAt:   time.Now(),
	}
	data, err := json.Marshal(p)
	if err != nil {
		return fmt.Errorf("marshalling pending: %w", err)
	}
	return os.WriteFile(pendingPath(dataDir), data, 0600)
}

func LoadPending(dataDir string) (*pendingUpdate, error) {
	data, err := os.ReadFile(pendingPath(dataDir))
	if os.IsNotExist(err) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("reading pending: %w", err)
	}
	var p pendingUpdate
	if err := json.Unmarshal(data, &p); err != nil {
		return nil, fmt.Errorf("parsing pending: %w", err)
	}
	return &p, nil
}

func ClearPending(dataDir string) {
	_ = os.Remove(pendingPath(dataDir))
}

// VerifyOrRollback is called at startup when update_pending.json exists.
// It waits for a successful heartbeat signal on heartbeatOK within timeout,
// then clears the sentinel and the .prev binary. If the timeout fires first,
// it rolls back the binary and restarts via restartFn.
func VerifyOrRollback(
	ctx context.Context,
	pending *pendingUpdate,
	binPath, dataDir string,
	heartbeatOK <-chan struct{},
	restartFn func(),
	apiURL, endpointID string,
) {
	timeout := 60 * time.Second
	// if the sentinel is very old (agent crashed and restarted), still give 60s
	select {
	case <-heartbeatOK:
		ClearPending(dataDir)
		_ = os.Remove(binPath + ".prev")
		_ = Report(apiURL, endpointID, pending.ToVersion, "success", "")
		fmt.Println("[update] post-update verification passed; update committed")

	case <-time.After(timeout):
		fmt.Fprintf(os.Stderr, "[update] verification timeout; rolling back to %s\n", pending.FromVersion)
		_ = Report(apiURL, endpointID, pending.ToVersion, "failed", "heartbeat timeout")
		if err := RollbackBinary(binPath); err != nil {
			fmt.Fprintf(os.Stderr, "[update] rollback failed: %v\n", err)
		}
		ClearPending(dataDir)
		restartFn()

	case <-ctx.Done():
	}
}
