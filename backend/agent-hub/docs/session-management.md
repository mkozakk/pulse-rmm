# Session Management (`infrastructure/desktop/`)

Manages the lifecycle of desktop and shell sessions between browser clients and agent endpoints.

## Session State Machine

```
[Idle] → [Pending] → [Ready] → [Active] → [Closed]
           ↓         ↓
       [Error]    [Timeout]
```

**Idle** — Session record created in database, no agent action yet
**Pending** — Hub has sent `StartDesktopSession` command to agent, awaiting readiness
**Ready** — Agent has prepared (screen capture started, PTY created), awaiting browser connection
**Active** — WebSocket/WebRTC connection established, session is live
**Closed** — Session terminated by user, agent, or timeout
**Error** — Unrecoverable error during startup (e.g., permission denied, agent offline)

## Desktop Session Lifecycle

### Initiation

1. Browser makes `POST /api/sessions` (to endpoint-service)
2. Endpoint-service checks user permissions and endpoint is online
3. Endpoint-service saves session record with `status=pending`
4. Endpoint-service calls agent-hub: `POST /internal/agents/{endpointId}/desktop/session`
5. Agent-hub looks up agent in registry, constructs `StartDesktopSession` command
6. Agent-hub pushes command onto agent's gRPC stream with session ID
7. Agent-hub returns `202 Accepted` and transitions session to `pending`

### Agent Preparation

1. Agent receives `StartDesktopSession` command
2. Agent (in desktop package) starts ffmpeg for screen capture (`gdigrab` on Windows, `x11grab` on Linux)
3. Agent creates Pion WebRTC PeerConnection with STUN/TURN ICE servers
4. Agent opens WebRTC video track (for screen), audio track (for sound)
5. Agent opens WebRTC data channels: `input` (for mouse/keyboard), `file-transfer` (for files)
6. Agent sends `SessionReady` message back to hub

### Hub Readiness Check

1. Hub receives `SessionReady`, transitions session to `ready`
2. Endpoint-service is notified (via event or polling)
3. Browser is notified session is ready to connect (via SSE or polling)

### Browser Connection & WebRTC Negotiation

1. Browser opens WebSocket to `ws://localhost:8092/ws/desktop/{sessionId}`
2. WebSocket handshake validates JWT and session exists
3. Browser creates RTCPeerConnection and generates SDP offer
4. Browser sends offer to hub via WebSocket
5. Hub forwards offer to agent via gRPC
6. Agent receives offer, calls `SetRemoteDescription(offer)`
7. Agent generates SDP answer, flushes queued ICE candidates
8. Agent sends answer back to hub
9. Hub forwards answer to browser via WebSocket
10. Browser calls `SetRemoteDescription(answer)`
11. Browser and agent exchange ICE candidates via Trickle ICE

### Connection Success

1. Once both sides have candidates and SDP is set, WebRTC negotiates a connection
2. If a direct peer-to-peer path exists (same network), use it
3. If both sides are behind NAT with no direct path, coturn relays the media
4. Media (video, audio, input, files) flows directly peer-to-peer; signaling continues over WebSocket
5. Hub transitions session to `active`

### Session Closure

**User-initiated:**
1. User closes browser tab or clicks "End Session"
2. Browser sends `DELETE /api/sessions/{sessionId}`
3. Endpoint-service marks session as `closed`
4. Hub sends `CloseSession` command to agent
5. Agent stops screen capture, closes WebRTC connection, tears down session

**Agent-initiated:**
1. Agent detects WebRTC connection closed (via `OnConnectionStateChange`)
2. Agent immediately tears down session
3. Agent sends `SessionClosed` message to hub
4. Hub marks session as `closed`

**Timeout:**
1. If session has no activity for N minutes (configurable, default 5), hub auto-closes
2. Hub sends `CloseSession` command to agent
3. Agent cleans up

## Desktop Session Registry

`DesktopSessionRegistry` maintains:
- Map of active sessions: `{sessionId → DesktopSession}`
- Per-agent session limit (concurrent sessions per endpoint)
- Session timeout tracking

Entries are cleaned up when session closes or times out.

## Orphaned Session Detection

If a `StartDesktopSession` command arrives for a session that no longer exists (browser gave up, endpoint-service deleted it), the agent will ignore it via the `endedBeforeStart` map tracking.

## Shell Session Lifecycle

Shell sessions follow a similar pattern but are simpler:

1. Browser requests shell session: `POST /api/sessions` with `type=shell`
2. Endpoint-service creates session record
3. Hub sends `OpenShellSession` command to agent
4. Agent spawns bash/zsh (Linux) or PowerShell/cmd (Windows) in a PTY/ConPTY
5. Browser opens WebSocket to `ws://localhost:8092/ws/shell/{sessionId}`
6. Hub proxies input/output between WebSocket and gRPC stream
7. When browser closes WebSocket or session closes, agent terminates the PTY

Multiple shell sessions can run simultaneously on the same endpoint (e.g., two technicians in separate shells).

## Permission Checks

Permission checks happen at multiple layers:

1. **Endpoint-service** (when session is created) — `endpoint:session:create` on the target endpoint
2. **WebSocket handshake** (in agent-hub) — `endpoint:shell:view` (for shell) or `endpoint:desktop:view` (for desktop)
3. **Agent** — No additional permission check (agent trusts the hub's command)

If a user loses a permission between session creation and WebSocket connection, the handshake will fail.

## Metrics & Observability

- Gauge: `sessions.active.desktop` — Current active desktop sessions
- Gauge: `sessions.active.shell` — Current active shell sessions
- Counter: `sessions.created` — Total sessions created
- Counter: `sessions.closed` — Total sessions closed (success)
- Counter: `sessions.failed` — Sessions that failed to initialize
- Timer: `session.startup.latency` — Time from `StartDesktopSession` to `SessionReady`
- Timer: `webrtc.negotiation.latency` — Time from offer to connection established

These are exported to Prometheus at `localhost:8092/actuator/prometheus`.
