# Remote Desktop & File Transfer (`src/hooks/useDesktopSession.js`, `src/pages/DesktopPage.jsx`)

A WebRTC remote-desktop session: signaling over WebSocket, H.264 video over a media track, input and file transfer over data channels.

### code
[`hooks/useDesktopSession.js`](../src/hooks/useDesktopSession.js):
	- `useDesktopSession` - Owns the whole session. Creates a session over REST (`useCreateSessionMutation`), builds an `RTCPeerConnection` from the returned STUN/TURN ICE servers, opens the signaling WebSocket (`/ws/sessions/{id}/signal?token=…`), and tears everything down on unmount or `endSession`.
	- Signaling handler - On `session_ready` it adds recvonly video/audio transceivers, creates the SDP offer, and sends it; it applies the `answer`, adds remote ICE candidates, and surfaces `error` codes. Local ICE candidates are trickled back over the WebSocket.
	- Input forwarding effect - When the session grants control, attaches mouse, keyboard, and wheel listeners to the `<video>` element and sends each event as JSON over the `input` data channel, scaling pointer coordinates to the remote resolution.
	- `sendFile` / `requestDownload` - Drive the `file-transfer` data channel: `sendFile` streams a file in 64 KB chunks bracketed by `upload_start`/`upload_done` messages; `requestDownload` asks the agent for a path.
	- `endSession` - Deletes the session over REST and closes the socket and peer connection.

[`pages/DesktopPage.jsx`](../src/pages/DesktopPage.jsx):
	- `DesktopPage` - Renders the remote `<video>`, the connection status, audio mute and fullscreen controls, and the file-transfer panel. Maps backend `error` codes (Wayland portal/consent issues, no logged-in user, …) to actionable operator messages.

[`components/FileTransferPanel.jsx`](../src/components/FileTransferPanel.jsx):
	- `FileTransferPanel` - Drag-and-drop / picker upload and a path-based download box, delegating to the hook's `sendFile` and `requestDownload`.

### description
Remote desktop is peer-to-peer media with a thin control plane. Only signaling (SDP offer/answer and ICE candidates) flows over the WebSocket; once the peer connection is established, video and every interaction travel directly over WebRTC. The hook is the single source of truth for session lifecycle — it guards against races (a session created after the component unmounts is immediately deleted) and always cleans up the REST session, socket, and peer connection on teardown.

Control is capability-gated: the input listeners and the `input` data channel are only created when the backend marks the session `canControl`, so a view-only session cannot send mouse or keyboard events. Pointer coordinates are scaled from the on-screen video size to the endpoint's real resolution before sending. File transfer rides a separate `file-transfer` data channel with a small chunked protocol so large uploads do not block signaling, and the agent enforces path-traversal protection on the far side. Failure modes are translated for the operator: rather than a raw code, `DesktopPage` explains, for example, that the endpoint's user declined the screen-share prompt or that ffmpeg lacks PipeWire support.
</content>
