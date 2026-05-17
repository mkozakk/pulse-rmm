# Service Lifecycle Management (`internal/svc/`)

Abstracts OS-level service management to run the agent as a system service on Linux (systemd) and Windows (Service Control Manager).

### code

[`service.go`](../internal/svc/service.go):
	- `Run(runFn)` - Wraps the agent logic with platform-specific service integration via the `kardianos/service` library. Blocks until the OS requests a stop (SIGTERM on Linux, SCM stop on Windows).
	- `Install()` - Registers the agent as a system service so it starts automatically on boot.
	- `Uninstall()` - Removes the service registration and stops it if running.
	- `Status()` - Queries the current service state (running, stopped).
	- `Restart()` - Requests the OS to restart the service. Used by the auto-update loop after a binary swap.

[`privilege_unix.go`](../internal/svc/privilege_unix.go):
	- `EnsureRoot()` - On Unix systems, verifies the agent is running as root; exits with error if not. (Desktop sessions may run as a user).

[`privilege_windows.go`](../internal/svc/privilege_windows.go):
	- `EnsureAdmin()` - On Windows, verifies the agent is running with Administrator privileges.

### description

The agent runs as a system service to ensure it stays running across user logins and reboots. Rather than implementing platform-specific service code, this module delegates to `kardianos/service`, which abstracts the differences between systemd (Linux) and the Windows Service Control Manager (SCM).

When `main()` receives `service install` or `service uninstall`, it calls the corresponding functions to register or unregister the service. The entry point `Run()` is a blocking function that yields control to the service manager's event loop. The manager emits `Start` and `Stop` signals to a `program` struct which implements the `service.Interface`. When `Start` is called, the actual agent logic (`runAgent`) is launched in a goroutine with a cancellable context. When `Stop` is called (triggered by SIGTERM on Linux or a service stop request on Windows), the context is cancelled, causing the agent to gracefully shut down its goroutines and exit.

For security, the agent enforces privilege requirements: it must run as `root` on Linux and as `Administrator` on Windows. The `EnsureRoot()` and `EnsureAdmin()` functions are called early in startup to validate this. Desktop-specific helper processes may run unprivileged (as the logged-in user) to access their display, but the main agent service must be elevated to manage system software, execute scripts, and configure network adapters.
