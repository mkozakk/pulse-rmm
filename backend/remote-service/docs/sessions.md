# Session Lifecycle (`api/`, `application/`, `domain/`, `infrastructure/`)

Manages the secure initiation, state tracking, and cleanup of interactive remote control features like Desktop sharing.

### code
**API & Routing**
[`SessionController.java`](../src/main/java/dev/pulsermm/remote/api/SessionController.java):
	- `createSession` — POST `/api/sessions`. Creates a desktop session with `pending` status, generates TURN credentials, checks `remote:desktop:control` permission, dispatches `StartDesktopSession` to the agent via the gateway, and returns `201 {sessionId, turnUrls, turnUsername, turnCredential, canControl}`.
	- `getSession` — GET `/api/sessions/{id}`. Returns `200 {id, status}`.
	- `endSession` — DELETE `/api/sessions/{id}`. Marks session `ended`, dispatches `EndDesktopSession` to the agent. Returns `204`.

**Data Transfer Objects (DTOs)**
[`CreateSessionRequest.java`](../src/main/java/dev/pulsermm/remote/api/dto/CreateSessionRequest.java) & [`CreateSessionResponse.java`](../src/main/java/dev/pulsermm/remote/api/dto/CreateSessionResponse.java):
	- Encapsulate the inbound payload (endpointId, type) and return the session ID, TURN URLs, TURN credentials, and `canControl` flag.

**Domain Exceptions**
[`SessionNotFoundException.java`](../src/main/java/dev/pulsermm/remote/application/SessionNotFoundException.java):
	- Thrown when querying a session that doesn't exist, triggering a 404 response.

**Application Logic & Integrations**
[`SessionService.java`](../src/main/java/dev/pulsermm/remote/application/SessionService.java):
	- Core orchestrator: persists `DesktopSession`, checks RBAC via `IdentityClient`, generates TURN credentials (HMAC-SHA1, 1-hour expiry), dispatches session lifecycle to the agent via `GatewayClient`.
[`GatewayClient.java`](../src/main/java/dev/pulsermm/remote/infrastructure/GatewayClient.java):
	- Internal HTTP client to the API Gateway's `/internal/desktop-sessions/start` and `/internal/desktop-sessions/end` endpoints.
[`IdentityClient.java`](../src/main/java/dev/pulsermm/remote/infrastructure/IdentityClient.java):
	- Internal HTTP client to verify the technician has the `remote:desktop:control` permission.
[`SessionCleanupJob.java`](../src/main/java/dev/pulsermm/remote/application/SessionCleanupJob.java):
	- `@Scheduled(fixedDelay=60s)`: marks sessions stuck in `pending` for more than 5 minutes as `ended`. Prevents orphan accumulation when the agent disconnects or the session flow is interrupted.

**Domain Entities & Repositories**
[`DesktopSession.java`](../src/main/java/dev/pulsermm/remote/domain/DesktopSession.java):
	- JPA entity with status transitions: `pending` → `ended`. Stores TURN credentials and timestamps.
[`SessionRepository.java`](../src/main/java/dev/pulsermm/remote/infrastructure/persistence/SessionRepository.java):
	- Spring Data JPA repository. Adds `findStalePending(cutoff)` for the cleanup job.

### description
Desktop sessions use a two-phase creation pattern. The frontend first calls `POST /api/sessions` to create a server-side session and obtain TURN credentials. The `SessionService` persists a `DesktopSession` (status `pending`), checks permissions, and dispatches `StartDesktopSession` to the agent via the gateway's internal REST API. The gateway forwards the command to the agent's gRPC stream, and the agent begins H.264 screen capture.

The response includes TURN URLs/credentials and a `canControl` flag (derived from RBAC). The frontend then opens a WebSocket to `/ws/sessions/{id}/signal` on the gateway. The gateway validates the session exists in the `DesktopSessionRegistry` (registered during the `/internal/desktop-sessions/start` call), then bridges WebSocket messages to the agent's gRPC stream for SDP and ICE candidate exchange.

Session teardown: the frontend calls `DELETE /api/sessions/{id}`, the service marks the session `ended` in the DB and dispatches `EndDesktopSession` to the agent. If the frontend's React effect is cancelled before the session fully establishes, a `deleteSession` call in the cancelled path prevents orphaned agent-side sessions.

A `SessionCleanupJob` runs every 60 seconds to find sessions stuck in `pending` for more than 5 minutes and marks them `ended`. This handles the case where the agent disconnects or the gRPC command race causes a session to never transition.

**Known race**: The gateway's internal `/internal/desktop-sessions/start` and `/internal/desktop-sessions/end` endpoints are concurrent HTTP calls. The `SynchronizedObserver` on the agent's gRPC stream serializes `onNext` calls but does not guarantee ordering between `StartDesktopSession` and `EndDesktopSession` for the same session. The agent mitigates this by tracking ended session IDs — a `HandleStartSession` that arrives after its corresponding `HandleEndSession` is silently rejected.
