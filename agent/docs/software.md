# Software Management (`internal/software/`)

Abstracts OS-level package management for software inventory and deployment.

### code
[`executor.go`](../internal/software/executor.go), [`executor_windows.go`](../internal/software/executor_windows.go), & [`executor_linux.go`](../internal/software/executor_linux.go):
	- `Execute` - Acts as a central router that parses incoming backend instructions. It explicitly blocks `install` and `update` actions, delegating those to the Scripting Engine, and routes `remove` actions to OS-specific handlers.
	- `osExecuteRemove` - Wraps the underlying execution logic to interface cleanly with diverse package managers across Windows (executing `UninstallString` from the Registry with silent flags) and Linux (smartly routing to `flatpak`, `dnf`, or `apt-get` with `sudo` fallback).

[`scanner.go`](../internal/software/scanner.go):
	- `SoftwareItem` - Provides a standardized data structure for installed applications, ensuring the backend receives a uniform format regardless of the host OS.
	- `Scan` - Serves as the primary entry point for background routines to trigger periodic, system-wide software inventory checks.

[`scan_unix.go`](../internal/software/scan_unix.go) & [`scan_windows.go`](../internal/software/scan_windows.go):
	- `scan` - Executes the actual data collection. On Windows, it reads directly from the standard `Uninstall` registry keys using `golang.org/x/sys/windows/registry`. On Linux, it executes native tools (`dpkg-query`, `dnf list`, `flatpak list`) and parses their output while filtering out noisy system libraries.

### description
Managing software across a diverse fleet of endpoints requires standardizing interactions with various operating system environments. Rather than relying on unreliable CLI output parsers (like parsing `winget list` or `apt list`), this module reads directly from the most reliable sources available. On Windows, the scanner iterates through the `LOCAL_MACHINE` and `CURRENT_USER` registry hives, extracting the exact `DisplayName` and `UninstallString`. On Linux, it uses `dpkg-query` and filters out `lib*` packages to separate actual applications from low-level dependencies, while also scanning `dnf` and Desktop `flatpak` apps seamlessly.

When the agent receives a software command from the control stream, it routes the request through the `Execute` router. Notably, the agent strictly limits itself to `remove` commands-`install` and `update` are disabled at the agent level to enforce the architectural principle that software installations (which often require accepting keys, setting up repos, or custom flags) should be performed declaratively via the Scripting or Policy Engines. 

For uninstallation, the agent uses a smart routing mechanism. On Windows, it triggers the registry's `UninstallString` directly (injecting `/qn` silent flags if it detects an MSI). On Linux, it checks the package ID and installed binaries to seamlessly route the command to `flatpak uninstall`, `dnf remove`, or `apt-get remove`, executing them via `sudo` if the agent isn't running as root. This gives IT Administrators a clean, reliable, and enterprise-grade view of the software installed across their fleet, coupled with simple one-click removals.
