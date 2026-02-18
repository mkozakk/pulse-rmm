# Remote Desktop (`desktop/`)

Manages the negotiation and streaming of peer-to-peer remote desktop sessions.

### code
[`handler.go`](../desktop/handler.go):
	- `Handler` - Tracks active WebRTC connections, processing incoming signaling commands while preventing duplicate or conflicting sessions.
	- `HandleStartSession` - Cleans up legacy sessions and initiates WebRTC negotiation by exchanging SDP offers and ICE candidates.
	- `HandleSignal` - Processes incoming WebRTC connection data to facilitate asynchronous NAT traversal and candidate exchange.

[`session.go`](../desktop/session.go):
	- `DesktopSession` - Encapsulates the Pion WebRTC peer connection, managing associated video tracks and data channels as a cohesive unit.

[`capture_linux.go`](../desktop/capture_linux.go) & [`capture_windows.go`](../desktop/capture_windows.go):
	- `StartCapture` - Interacts with platform-specific display server APIs to continuously grab screen frames for video encoding.

[`input.go`](../desktop/input.go), [`input_linux.go`](../desktop/input_linux.go) & [`input_windows.go`](../desktop/input_windows.go):
	- `HandleInput` - Translates remote coordinates and clicks into native system events to simulate real hardware interactions on the host OS.

[`filetransfer.go`](../desktop/filetransfer.go):
	- `HandleFileTransfer` - Manages bidirectional data chunking, enabling technicians to upload or download files directly through the WebRTC data channel.

### description
Providing low-latency remote desktop capabilities requires bypassing the standard backend routing to establish direct or relayed connections between the endpoint and the administrator's browser. This is achieved using WebRTC technology, implemented via the Pion library, allowing for efficient media streaming and input handling. When a session initialization command is received, the `Handler` first locks its `sync.Mutex` to terminate any stuck or lingering capture routines from previous session attempts, preventing zombie goroutines. The agent then begins the WebRTC negotiation phase, exchanging session descriptions via the established gRPC control stream. Once the peer connection is successfully negotiated, the application launches a goroutine to continuously capture the host operating system's screen buffer, encode the frames into H.264, and transmit them over a dedicated WebRTC video track. Concurrently, a separate data channel is opened with an event listener attached to receive incoming mouse and keyboard payloads, which the input module translates and injects directly into the host OS input subsystem using native system calls.
