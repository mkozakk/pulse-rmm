# Remote Terminal / Shell (`internal/shell/`)

Provides the interactive pseudo-terminal proxy capabilities.

### code
[`manager.go`](../internal/shell/manager.go):
	- `Manager` - Tracks the state of active sessions to safely support multiple terminal tabs opened concurrently by different administrators.
	- `Open` - Initializes a new PTY session by binding a unique session identifier to a newly spawned local subprocess.
	- `Input` - Writes byte arrays directly to a specific session, piping user keystrokes from the browser to the correct local shell.
	- `Close` / `CloseAll` - Terminate running sessions to cleanly release operating system resources and goroutines upon tab closure or agent shutdown.

[`session.go`](../internal/shell/session.go):
	- `Session` - Defines the core contract for reading, writing, resizing, and closing, ensuring consistent terminal behavior across different operating systems.

[`session_unix.go`](../internal/shell/session_unix.go):
	- `Start` - Spawns a bash process using Linux-specific `pty` system calls to simulate a native interactive terminal.

[`session_windows.go`](../internal/shell/session_windows.go):
	- `Start` - Spawns a command prompt or PowerShell instance, utilizing Windows-specific console proxy mechanisms like ConPTY.

### description
To allow administrators to troubleshoot devices directly from their browser, the agent provides a live, interactive terminal proxy. Unlike static script execution, this feature requires managing continuous input and output streams in real-time without blocking the rest of the application. When a terminal session request arrives, the manager initiates a new pseudo-terminal process on the host operating system. Because multiple administrators might open terminal tabs simultaneously, the `Manager` tracks active sessions in a map protected by a `sync.Mutex`, ensuring thread-safe access across concurrent network events. For each active session, a dedicated goroutine is launched to continuously read the terminal's standard output. As data chunks are read, they are wrapped in protobuf events and pushed into the out channel (`chan<- *pb.AgentEvent`) to be routed back to the backend. Simultaneously, incoming keystrokes from the browser are routed through the `Input` mechanism, looking up the correct session interface implementation in the map, and injecting the bytes directly into the local shell session's standard input stream.
