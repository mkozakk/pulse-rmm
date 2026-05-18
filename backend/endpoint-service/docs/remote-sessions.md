# Remote Sessions

## Overview

The Remote Sessions subsystem coordinates WebRTC-based remote access (desktop, shell, file transfer) between technicians in the browser and agents on endpoints. The service manages session lifecycle, WebRTC signaling, and cleanup.

## Session Types

```
DESKTOP   — Screen capture, keyboard/mouse input, file transfer
SHELL     — Terminal multiplexing (WebSocket proxy)
HELP      — Built-in help request from endpoint user
```

## Session Lifecycle

```
1. Technician opens remote desktop view in browser
   → Browser calls POST /api/sessions {endpoint_id, type=desktop}

2. API Gateway validates permission (remote:desktop:control)

3. Endpoint Service checks endpoint is online, creates session record
   → Session status = pending
   → Generates session_id (UUID)

4. Service sends gRPC StartSession {session_id, type} to agent
   └─ Agent receives, initializes encoder/capture

5. Agent responds with SessionReady
   → Service updates session status = active

6. Browser opens WebSocket to /ws/sessions/{id}
   → WebSocket connection is authenticated (JWT in query param)

7. Browser sends SDP offer (WebRTC initialization)
   → Routed to agent via gRPC Signal {sdp_offer}

8. Agent probes available encoders, generates SDP answer
   → Agent sends gRPC Signal {sdp_answer}
   → Service broadcasts to browser

9. ICE candidate exchange (multiple messages)
   → Enables NAT traversal via STUN/TURN relay

10. WebRTC stream established
    → Agent sends video, audio, file transfer data channels
    → Browser receives and displays/plays back

11. Session ends:
    - Browser closes window → WebSocket closes
    - Or technician clicks "disconnect"
    - Service sends gRPC TerminateSession to agent

12. Agent shuts down capture, closes stream
    → Session status = closed
    → Record marked for cleanup (audit log retention)
```

## Database Schema

```sql
desktop_sessions(
  id uuid pk,
  endpoint_id uuid fk (endpoints),
  type varchar,                   -- desktop, shell, help
  session_creator uuid fk (users),
  status varchar,                 -- pending, active, closed, failed
  created_at timestamptz,
  started_at timestamptz,         -- when agent confirmed ready
  closed_at timestamptz,          -- when session ended
  close_reason varchar,           -- user-closed, timeout, error
  duration_seconds int,           -- closed_at - started_at
  frame_count bigint,             -- for metrics
  bytes_sent bigint,
  bytes_received bigint
)

-- audit trail: who connected to which endpoint and when
session_audit_log(
  id uuid pk,
  session_id uuid fk (desktop_sessions),
  endpoint_id uuid,
  technician_id uuid,
  event varchar,                  -- connected, disconnected, stream_error
  details text,
  created_at timestamptz
)
```

## WebRTC Signaling

The API Gateway routes WebRTC signaling messages between browser and agent:

```
Browser                 Gateway                 Agent
   |                       |                      |
   +--POST /sessions-------→|
   |                        |--gRPC StartSession-→|
   |                        |←--SessionReady------|
   |                    (session created)
   |
   +--WebSocket /ws/sessions/{id}--→|
   |                                 |
   +--SDP offer----→|--gRPC Signal--→|
   |               |←--SDP answer----|--→|
   |               |                  |
   +--ICE cand.----→|--gRPC Signal--→|
   |               |←--ICE cand.----|--→|
   |               |                  |
(WebRTC stream established - media flows directly via TURN relay)
```

**Why gRPC for signaling?**
- Agent has persistent gRPC control stream
- Service can push messages to agent without polling
- Binary efficiency for repeated ICE candidates

**Browser-to-Service connection:**
- WebSocket (stateful, bidirectional)
- Authenticated with JWT (session cookie or Authorization header)
- Sent over HTTPS/WSS

## Permission Enforcement

Every session creation checks permissions:

```java
@PostMapping
public ResponseEntity<CreateSessionResponse> createSession(
    @RequestBody CreateSessionRequest req,
    @AuthenticationPrincipal JwtAuthToken auth
) {
    // 1. Verify endpoint exists
    Endpoint endpoint = endpointRepository.findById(req.endpointId())
        .orElseThrow(NotFoundException::new);
    
    // 2. Check permission (e.g., remote:desktop:control)
    String permission = switch (req.type()) {
        case DESKTOP → "remote:desktop:control";
        case SHELL → "remote:shell:control";
        case HELP → "remote:help:respond";
    };
    
    if (!identityService.hasPermission(auth.userId(), endpoint.groupId(), permission)) {
        throw new PermissionDeniedException(permission);
    }
    
    // 3. Verify endpoint is online
    if (endpoint.status() != Status.ONLINE) {
        throw new EndpointOfflineException();
    }
    
    // 4. Create session...
}
```

## Session Cleanup

Completed sessions are kept in the database for audit purposes (30 days retention), but WebRTC connections are immediately closed:

- Agent shuts down capture process
- Browser WebSocket is closed
- Session status = closed
- Metrics (duration, bytes) are recorded
- Nightly job purges sessions older than 30 days

## Error Handling

- `400 Bad Request` — Invalid session type
- `401 Unauthorized` — Missing/invalid JWT
- `403 Forbidden` — User lacks remote:*:control permission
- `404 Not Found` — Endpoint not found
- `409 Conflict` — Endpoint offline, session already exists for endpoint
- `500 Internal Server Error` — gRPC communication error, database error

## Edge Cases

**Simultaneous sessions on same endpoint:**
- Only one remote desktop session per endpoint at a time
- Multiple help/shell sessions allowed (future)
- Attempt to create second session returns 409 Conflict

**Agent crashes during session:**
- Heartbeat timeout (>90s) marks endpoint offline
- Service closes browser WebSocket
- Browser shows "endpoint disconnected"

**Browser closes without calling close endpoint:**
- WebSocket disconnect is detected
- Service sends gRPC TerminateSession to agent
- Agent stops capture
- Session is marked closed
