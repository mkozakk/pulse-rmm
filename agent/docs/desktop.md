# Remote Desktop (`desktop/`)

Manages the negotiation and streaming of peer-to-peer remote desktop sessions.

### code
[`handler.go`](../desktop/handler.go):
	- `Handler` - Orchestrates session lifecycle. Tracks active sessions, processes signaling, rejects stale `HandleStartSession` via `endedBeforeStart` map.
	- `HandleStartSession` - Creates a Pion PeerConnection (STUN + TURN ICE servers), registers `OnICECandidate` for Trickle ICE, starts ffmpeg capture via `startCapture`, sends `session_ready`. Kills existing sessions before storing the new one. Rejects if the session ID was already ended (gateway ordering race mitigation).
	- `HandleSignal` - Routes incoming WebRTC signals: `offer` → `HandleOffer` (returns answer), `candidate` → `AddICECandidate` (queues if remote desc not yet set).
	- `HandleEndSession` - Removes session from map, cancels capture context, calls `sess.Close()`. Tracks ended session IDs in `endedBeforeStart` to reject delayed `HandleStartSession`.
	- `endedBeforeStart` - Map of session IDs that received `HandleEndSession` before `HandleStartSession`. `HandleStartSession` checks this map and returns early if found, preventing stale starts from killing the active session.

[`session.go`](../desktop/session.go):
	- `DesktopSession` - Encapsulates the Pion WebRTC peer connection, video track, input injector, rate limiter, pending ICE candidate queue, and `closeOnce` + mutex for safe teardown.
	- `HandleOffer` - Sets remote description, flushes queued ICE candidates, creates answer. Protected by `sync.Mutex`.
	- `AddICECandidate` - Queues ICE candidates if `RemoteDescription()` is nil (Trickle ICE), adds them otherwise.
	- `Close` - Guards with `closeOnce` and `sync.Mutex` to prevent double-close races. Closes injector, PeerConnection, and log file.
	- `createOnce` - Prevents `Close()` from executing more than once, even when called from both `HandleEndSession` and the `OnPeerConnectionClosed` callback.

[`capture_linux.go`](../desktop/capture_linux.go) & [`capture_windows.go`](../desktop/capture_windows.go):
	- `startCapture` - Interacts with platform-specific display server APIs to continuously grab screen frames for H.264 video encoding via ffmpeg.

[`input.go`](../desktop/input.go), [`input_linux.go`](../desktop/input_linux.go) & [`input_windows.go`](../desktop/input_windows.go):
	- `InputInjector` interface with platform implementations (X11/uinput for Linux, SendInput for Windows). Rate-limited at 60 events/sec.

[`filetransfer.go`](../desktop/filetransfer.go):
	- Bidirectional file transfer over WebRTC data channel. Uploads stored to `/tmp/pulse-uploads/`. Downloads restricted to home directory (path traversal protection via `filepath.Rel`).

### Video Capture Subsystem

[`capture/capture.go`](../desktop/capture/capture.go):
	- `startCapture(ctx, sess)` - Entry point that selects the platform-appropriate capture method and launches a dedicated goroutine to continuously grab screen frames and encode them to H.264.

[`capture/linux.go`](../desktop/capture/linux.go):
	- `capture` - On Linux, spawns an ffmpeg process with `x11grab` (X11) or `kmsgrab` (Wayland via PipeWire) to read the framebuffer. Reads encoded H.264 NAL units from ffmpeg stdout.

[`capture/windows.go`](../desktop/capture/windows.go):
	- `capture` - On Windows, spawns ffmpeg with `gdigrab` (GDI Desktop Duplication API). Reads encoded H.264 NAL units.

[`capture/env_linux.go`](../desktop/capture/env_linux.go):
	- `selectDisplayServer()` - Detects whether the user is on X11 (DISPLAY env var) or Wayland (WAYLAND_DISPLAY). Routes to the correct capture backend.

The capture goroutine continuously reads H.264 NAL units from ffmpeg, assembles them into access units (complete frames), and writes them to a Pion `TrackLocalStaticSample` at 30fps. If the capture fails (ffmpeg crashes, display goes off), the context is cancelled and the session is torn down.

### Input Injection Subsystem

[`input/input.go`](../desktop/input/input.go):
	- `InputInjector` interface - Defines the contract for injecting mouse movements, clicks, and keyboard events.
	- `rate limiter` - Restricts input events to 60 per second to prevent overwhelming the OS or consuming excessive CPU.

[`input/linux.go`](../desktop/input/linux.go):
	- `Linux` - Uses `/dev/uinput` (user-space input device driver) to inject mouse and keyboard events. Requires the `99-pulse-agent-uinput.rules` udev rule to grant permissions without requiring the agent to run as root for input injection.

[`input/windows.go`](../desktop/input/windows.go):
	- `Windows` - Uses the native `SendInput` Win32 API to inject mouse movements and keyboard events with nanosecond-precision timing.

Input events arrive over the WebRTC `input` DataChannel from the browser. The session's DataChannel message handler decodes them, applies rate limiting, and calls the platform-specific injector.

### File Transfer Subsystem

[`transfer/transfer.go`](../desktop/transfer/transfer.go):
	- `Upload(file)` - Receives files from the browser over the WebRTC `file-transfer` DataChannel. Saves them to `/tmp/pulse-uploads/` with a randomized name to prevent collisions.
	- `Download(path)` - Sends files from the agent to the browser. Validates the requested path via `filepath.Rel()` to ensure it is within the user's home directory, preventing directory traversal attacks.

File transfer uses a separate WebRTC DataChannel (`file-transfer`) to avoid head-of-line blocking on the input channel.

### Desktop Helper Process

[`helper_linux.go`](../desktop/helper_linux.go), [`helper_windows.go`](../desktop/helper_windows.go), [`helper_proxy_linux.go`](../desktop/helper_proxy_linux.go):
	- `RunHelper(addr)` - On non-Wayland systems, spawns a helper process that runs as the logged-in user (not root). Used to capture the desktop from the user's X11 session. On Windows, the main service process captures directly. On Linux+Wayland, the helper manages PipeWire connections.
	- `helper_proxy_linux.go` - Routes the desktop session through a Unix socket if the agent runs as root but needs to access a user's Wayland display.

The helper process is invoked with the `--desktop-helper` flag and communicates with the main agent over a local gRPC connection.

### description
Remote desktop uses WebRTC via the Pion library to establish direct or TURN-relayed media streams between the agent and browser. The signaling path goes through the gRPC control stream and the API Gateway's WebSocket bridge.

When `HandleStartSession` is received, the agent creates a Pion PeerConnection with STUN (`stun:stun.l.google.com:19302`) and TURN (from backend config) ICE servers. Video capture starts immediately, grabbing frames from the OS display via ffmpeg or platform-specific APIs. The capture goroutine reads H.264 NAL units from ffmpeg stdout, assembles them into access units, and writes them to a `TrackLocalStaticSample` at 30fps.

The frontend receives `session_ready`, creates its own RTCPeerConnection, and sends an SDP offer via WebSocket. The Gateway relays the offer through the gRPC stream to `HandleSignal` → `HandleOffer`. The agent sets the remote description, flushes any pending ICE candidates (Trickle ICE queued before the offer arrived), creates and sends back an SDP answer.

ICE candidates flow bidirectionally via Trickle ICE throughout the negotiation — neither side waits for gathering to complete. Candidates arriving before `SetRemoteDescription` are queued in `pendingICE` and flushed atomically in `HandleOffer`.

Input events (mouse, keyboard) arrive over a separate WebRTC DataChannel at up to 60Hz (rate-limited). They are injected into the OS using platform-specific mechanisms: uinput on Linux, SendInput on Windows.

File transfers use a third DataChannel to avoid blocking input. Uploads are validated to prevent path traversal; downloads are restricted to the user's home directory.

Session teardown happens via `HandleEndSession` (triggered by `DELETE /api/sessions/{id}` from the frontend) or via the `OnPeerConnectionClosed` callback (triggered when the WebRTC connection drops). In both cases, the capture context is cancelled (killing ffmpeg) and `sess.Close()` is called (closing the injector, PeerConnection, and log file). The `closeOnce` guard prevents double-close if both paths fire.

A `endedBeforeStart` map tracks session IDs that received `HandleEndSession` before the corresponding `HandleStartSession` — this handles the gateway-level race where HTTP requests for `start` and `end` can arrive at the agent's gRPC stream out of order, preventing a stale `HandleStartSession` from killing the currently active session.
