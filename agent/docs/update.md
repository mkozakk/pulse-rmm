# Agent Auto-Update (`internal/update/`)

Handles checking for new versions, downloading updates, safely swapping the binary, and rolling back on failure.

### code

[`loop.go`](../internal/update/loop.go):
	- `Updater` - Orchestrates the auto-update loop. Checks for new versions periodically, downloads if available, and initiates a binary swap.
	- `Start(ctx)` - Spawns a background goroutine that periodically calls the backend's update checker endpoint to see if a newer version is available. If yes, triggers a download and swap.

[`checker.go`](../internal/update/checker.go):
	- `CheckForUpdate(apiURL, endpointID, currentVersion)` - Makes an HTTP GET call to the backend to ask: "Is there a newer version than what I'm running?"

[`downloader.go`](../internal/update/downloader.go):
	- `Download(apiURL, version, dataDir)` - Fetches the new agent binary (matching the current platform) from the backend. Saves it to a temporary location in `dataDir`.

[`swap.go`](../internal/update/swap.go):
	- `Swap(currentBin, newBin)` - Platform-specific binary replacement logic. On Unix, uses atomic `rename`; on Windows, uses a registry-based deferred swap that triggers on next reboot via `swap_windows.go`.

[`swap_unix.go`](../internal/update/swap_unix.go):
	- `swap` - Atomically replaces the running agent binary using `os.Rename`, allowing the service manager to restart the new version.

[`swap_windows.go`](../internal/update/swap_windows.go):
	- `swap` - Queues the new binary in the Windows Registry under `MoveFileEx` pending operations, triggering the swap on next reboot. The service manager then restarts with the new binary.

[`verify.go`](../internal/update/verify.go):
	- `VerifyOrRollback(ctx, pending, binPath, dataDir, heartbeatOK, restartFn, apiURL, endpointID)` - Runs after a binary swap on the next boot. Waits for the first successful heartbeat to confirm the new binary is healthy. If no heartbeat within a timeout, rolls back to the previous version and restarts.

### description

To keep agents current without manual intervention, the agent implements an auto-update loop. Every check interval (configurable via backend), the `Updater.Start()` goroutine calls `CheckForUpdate()` to ask the backend if a newer version is available. If yes, it downloads the new binary via `Download()` and calls `Swap()` to replace the running executable.

On Unix (Linux), the swap is atomic: the old binary is immediately replaced with the new one. The service manager (systemd) detects the change and restarts the agent process with the new binary.

On Windows, atomic binary replacement is not possible (the running executable cannot be moved while in use). Instead, `Swap()` writes a registry entry queuing the new binary to be moved on next reboot. The service manager then reboots the machine or the agent is manually restarted, triggering the OS to perform the swap before the service starts.

After a binary swap, the agent must verify that the new version is healthy. The `VerifyOrRollback()` function stores a "pending" state file before the swap, detects it on next boot, and sets a timeout (e.g., 60 seconds) waiting for the first successful heartbeat from the new binary. If the heartbeat succeeds, the agent is healthy and the pending state is cleared. If the timeout expires, the update is considered failed, the previous binary is restored, and the service is restarted with the known-good version. This rollback safety ensures that a bad update does not permanently brick the agent.
