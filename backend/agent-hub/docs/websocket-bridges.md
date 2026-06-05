# WebSocket Bridges (`infrastructure/ws/`)

Bridges WebSocket connections from the browser (for shell and desktop signaling) to agent gRPC streams.

## Overview

The browser opens two separate WebSocket connections to the hub:
1. **Shell WebSocket** — For terminal I/O with a remote shell session
2. **Desktop Signaling WebSocket** — For WebRTC offer/answer/ICE candidates

The hub translates messages between WebSocket (text frame) and gRPC (protobuf).

## Shell WebSocket Bridge

**Endpoint:** `ws://localhost:8092/ws/shell/{sessionId}`

**Authentication:**
- Requires valid JWT in the query parameter or cookie
- Extracted by `ShellHandshakeInterceptor` before the WebSocket upgrade
- JWT claims include `sub` (user ID) and `scopes` (permissions)

**Message Format (JSON over WebSocket):**

Browser → Hub:
```json
{"type": "input", "data": "ls -la\n"}
```

Hub → Browser:
```json
{"type": "output", "data": "total 42\ndrwxr-xr-x  2 user user 4096 ...\n"}
{"type": "closed", "reason": "session terminated"}
```

**Implementation:**
- `ShellWebSocketHandler` extends `TextWebSocketHandler`
- On connection: Resolves session ID and validates user permissions to access the endpoint
- On message: Converts text frame to `ShellInput` protobuf, sends to agent
- On message from agent: Receives `ShellOutput` protobuf, converts to JSON, sends to browser
- On disconnect: Sends `CloseSession` command to agent

## Desktop Signaling WebSocket Bridge

**Endpoint:** `ws://localhost:8092/ws/desktop/{sessionId}`

**Authentication:**
- Same JWT validation as shell

**Message Format (JSON over WebSocket):**

Browser → Hub (WebRTC signaling):
```json
{"type": "offer", "sdp": "v=0\no=..."}
{"type": "ice_candidate", "candidate": "..."}
```

Hub → Browser:
```json
{"type": "answer", "sdp": "v=0\no=..."}
{"type": "ice_candidate", "candidate": "..."}
{"type": "connected"}
{"type": "error", "reason": "..."}
```

**Implementation:**
- `DesktopSignalingWebSocketHandler` extends `TextWebSocketHandler`
- On connection: Resolves session ID, validates permissions, instantiates `DesktopSignalingRouter`
- Incoming messages are queued (Trickle ICE pattern) until the remote description is set
- `DesktopSignalingRouter` forwards signaling messages to the agent and vice versa
- Once WebRTC connection is established, the media (video, audio, input, files) travels directly peer-to-peer; only signaling continues over WebSocket

## Handshake & Validation

`ShellHandshakeInterceptor` and `DesktopSignalingHandshakeInterceptor` intercept the WebSocket upgrade request:

1. Extract JWT from query parameter or `Authorization` header
2. Validate JWT signature and expiration
3. Resolve the session ID and check the endpoint is online
4. Check user has permission to access this endpoint (e.g., `endpoint:shell:open` or `endpoint:desktop:view`)
5. If all checks pass, allow upgrade; otherwise respond with `403 Forbidden`

## Error Handling

**Common Error Responses:**
- `401 Unauthorized` — Missing or invalid JWT
- `403 Forbidden` — Valid JWT but insufficient permissions to access endpoint
- `404 Not Found` — Session not found or endpoint not connected
- `409 Conflict` — Session is being accessed by another user (concurrent limit)

## Metrics & Observability

- Gauge: `ws.shell.connections.active` — Current open shell WebSocket connections
- Gauge: `ws.desktop.signaling.active` — Current open desktop signaling connections
- Timer: `ws.message.latency` — Time from browser message to agent response
- Counter: `ws.messages.sent` — Total WebSocket messages sent to browsers
- Counter: `ws.messages.received` — Total WebSocket messages received from browsers

## Concurrency Notes

Multiple threads handle WebSocket messages:
- Tomcat thread pool receives WebSocket frames
- When a frame arrives, the handler queues it onto the agent stream via `StreamObserver.onNext()`
- The `SynchronizedObserver` wrapper ensures thread safety (see [gRPC Agent Streams](grpc-streams.md))

The reverse direction (agent → browser) is also multi-threaded:
- The gRPC message handler receives frames from the agent
- It pushes them onto the WebSocket session (which buffers and sends asynchronously)
