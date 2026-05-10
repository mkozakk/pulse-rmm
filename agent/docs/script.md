# Script Execution (`internal/script/`)

Handles the secure execution of raw shell scripts pushed by the backend.

### code
[`executor_unix.go`](../internal/script/executor_unix.go):
	- `Execute` - Generates and executes a temporary bash script using `sh` or `bash` to handle raw shell execution on Linux and macOS.

[`executor_windows.go`](../internal/script/executor_windows.go):
	- `Execute` - Generates and executes a temporary `.ps1` file via PowerShell, accommodating Windows-specific execution policies and binaries.

### description
When an administrator triggers a remote script via the web interface, the raw text of that script is delivered to the agent through the control stream. This execution module is responsible for safely translating that text block into an active system process and capturing its outcome. The process begins by using `os.CreateTemp` to generate a temporary file on the host operating system with a randomized name to prevent collisions. The raw script content is written into this file, and the file permissions are modified (`os.Chmod`) to make it executable. A `defer os.Remove` statement is strategically placed to guarantee the temporary file is deleted immediately after the routing returns, preventing disk clutter. A new subprocess is then spawned using `exec.Command` pointing to the native shell environment. The module maps any provided environment variables into the subprocess's `Env` array and calls `CombinedOutput()` to block the goroutine until the script finishes. This captures all standard output and standard error streams into a single byte array, which is then parsed alongside the process exit code and returned to the control stream dispatcher.
