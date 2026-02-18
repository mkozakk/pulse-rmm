# Software Management (`internal/software/`)

Abstracts OS-level package management for software inventory and deployment.

### code
[`executor.go`](../internal/software/executor.go):
	- `Execute` - Acts as a central router that parses incoming backend instructions and dispatches them to the appropriate platform handler.
	- `executeInstall`, `executeUpdate`, `executeRemove` - Wrap the underlying execution logic to interface cleanly with diverse package managers across Windows and Linux.

[`scanner.go`](../internal/software/scanner.go):
	- `SoftwareItem` - Provides a standardized data structure for installed applications, ensuring the backend receives a uniform format regardless of the host OS.
	- `Scan` - Serves as the primary entry point for background routines to trigger periodic, system-wide software inventory checks.

[`scan_unix.go`](../internal/software/scan_unix.go) & [`scan_windows.go`](../internal/software/scan_windows.go):
	- `scan` - Executes native tools like `dpkg` or `choco` and parses their raw terminal output into structured Go data.

### description
Managing software across a diverse fleet of endpoints requires standardizing interactions with various operating system environments. Rather than implementing custom installation logic, this module acts as a wrapper around established native package managers, specifically Chocolatey for Windows and APT for Linux distributions. When the agent receives a software command from the control stream, it routes the request through the `Execute` router. This component uses a `switch` statement to evaluate the requested action and inspects `runtime.GOOS` to determine the underlying operating system. It then leverages the `os/exec` package to spawn a subprocess, translating the generic command into the specific CLI syntax required by the local package manager (e.g., `apt-get install -y`). Additionally, a separate scanning loop runs on a scheduled interval using `time.Ticker`, invoking the OS-specific `scan` mechanism. This executes the local package manager in listing mode, reads the standard output stream, parses the raw text into a slice of `SoftwareItem` data, and returns it so the agent can synchronize its inventory state via gRPC.
