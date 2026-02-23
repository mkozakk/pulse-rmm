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

### description
Remote desktop uses WebRTC via the Pion library to establish direct or TURN-relayed media streams between the agent and browser. The signaling path goes through the gRPC control stream and the API Gateway's WebSocket bridge.

When `HandleStartSession` is received, the agent creates a Pion PeerConnection with STUN (`stun:stun.l.google.com:19302`) and TURN (from backend config) ICE servers. Ffmpeg capture starts immediately, grabbing frames from the OS display (gdigrab on Windows, x11grab/kmsgrab on Linux). The capture goroutine reads H.264 NAL units from ffmpeg stdout, assembles them into access units, and writes them to a `TrackLocalStaticSample` at 30fps.

The frontend receives `session_ready`, creates its own RTCPeerConnection, and sends an SDP offer via WebSocket. The Gateway relays the offer through the gRPC stream to `HandleSignal` → `HandleOffer`. The agent sets the remote description, flushes any pending ICE candidates (Trickle ICE queued before the offer arrived), creates and sends back an SDP answer.

ICE candidates flow bidirectionally via Trickle ICE throughout the negotiation — neither side waits for gathering to complete. Candidates arriving before `SetRemoteDescription` are queued in `pendingICE` and flushed atomically in `HandleOffer`.

Session teardown happens via `HandleEndSession` (triggered by `DELETE /api/sessions/{id}` from the frontend) or via the `OnPeerConnectionClosed` callback (triggered when the WebRTC connection drops). In both cases, the capture context is cancelled (killing ffmpeg) and `sess.Close()` is called (closing the injector, PeerConnection, and log file). The `closeOnce` guard prevents double-close if both paths fire.

A `endedBeforeStart` map tracks session IDs that received `HandleEndSession` before the corresponding `HandleStartSession` — this handles the gateway-level race where HTTP requests for `start` and `end` can arrive at the agent's gRPC stream out of order, preventing a stale `HandleStartSession` from killing the currently active session.
