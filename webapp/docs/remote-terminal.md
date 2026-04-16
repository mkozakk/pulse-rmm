# Remote Terminal (`src/pages/TerminalPage.jsx`)

A browser terminal (xterm.js) bridged to the agent's shell over an authenticated, binary-framed WebSocket.

### code
[`pages/TerminalPage.jsx`](../src/pages/TerminalPage.jsx):
	- `TerminalPage` - Instantiates an xterm.js `Terminal` with the `FitAddon`, opens `ws://…/ws/shell/{id}?token=<jwt>` with `binaryType = 'arraybuffer'`, and wires terminal I/O to the socket.
	- `term.onData` handler - Encodes keystrokes to UTF-8 and sends them as a binary frame tagged with a `0x01` (input) opcode byte.
	- `ws.onmessage` handler - Reads incoming binary frames; frames tagged `0x01` (output) are written to the terminal.
	- `handleResize` - On container resize, refits the terminal and sends a `0x02` (resize) frame carrying the new column and row counts as two big-endian `uint16`s so the remote PTY matches the viewport.
	- `ws.onclose` handler - Prints `[session closed]` and disables the view.
	- Fullscreen handling - Tracks `fullscreenchange` to toggle a fullscreen terminal layout.

### description
The terminal is a direct pipe: the page does no command interpretation, it just moves bytes between xterm.js and the WebSocket. The gateway bridges that socket to the agent's pseudo-terminal, so what the operator types reaches a real shell on the endpoint and its output streams straight back.

The wire protocol is a minimal one-byte-opcode binary framing — `0x01` for shell I/O in both directions and `0x02` for resize — which keeps control messages (window size) on the same channel as data without a second connection or a JSON envelope. Because the browser `WebSocket` API cannot attach an `Authorization` header, the JWT is passed as a `?token=` query parameter, the same convention the desktop signaling and SSE streams use; the gateway validates it during the upgrade handshake. Resize events are sent whenever the container changes so the remote PTY's dimensions track the visible terminal, including when entering or leaving fullscreen.
</content>
