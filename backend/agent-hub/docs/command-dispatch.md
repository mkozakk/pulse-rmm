# Command Dispatch (`api/internal/`)

How commands from backend services are pushed onto agent streams and delivered to endpoints.

## Command Flow

1. **Creation** — commands-service creates a script/software/process command with `status=pending`
2. **Dispatch Request** — commands-service calls `/internal/dispatch/{endpointId}` on agent-hub with the command payload
3. **Lookup** — Hub looks up the endpoint in AgentRegistry
   - **If found:** Push command onto the stream immediately
   - **If not found:** Respond with `404 Not Found` (caller will retry or mark offline)
4. **Agent Execution** — Agent receives command, dispatches it to the appropriate handler (script executor, software manager, process controller)
5. **Result** — Agent sends result back on the stream
6. **Hub Capture** — Hub receives result and forwards to commands-service
7. **Persistence** — commands-service updates command to `status=done` and publishes completion event to RabbitMQ

## Internal Dispatch Endpoints

All endpoints are in the `api/internal/` package and are protected by an `@InternalOnly` filter (checks caller's client certificate issuer is internal).

### POST `/internal/agents/{endpointId}/script`
Dispatch a script command.

**Request Body:**
```json
{
  "commandId": "uuid",
  "script": "#!/bin/bash\necho hello",
  "arguments": ["arg1", "arg2"],
  "timeoutSeconds": 300,
  "secrets": {"VAR1": "encryptedBase64..."}
}
```

**Response:**
- `202 Accepted` — Command queued for delivery
- `404 Not Found` — Endpoint not connected
- `400 Bad Request` — Invalid command payload

### POST `/internal/agents/{endpointId}/software`
Dispatch a software command (install, update, remove).

**Request Body:**
```json
{
  "commandId": "uuid",
  "action": "install|update|remove",
  "packageName": "curl",
  "version": "7.85.0",
  "timeoutSeconds": 300
}
```

### POST `/internal/agents/{endpointId}/process`
Dispatch a process command (list or kill).

**Request Body:**
```json
{
  "commandId": "uuid",
  "action": "list|kill",
  "processId": 1234
}
```

### POST `/internal/agents/{endpointId}/desktop/session`
Start a desktop session (screen capture + WebRTC).

**Request Body:**
```json
{
  "sessionId": "uuid",
  "quality": "high|medium|low",
  "captureAudio": true
}
```

### POST `/internal/agents/{endpointId}/shell/session`
Start a shell session (PTY/ConPTY).

**Request Body:**
```json
{
  "sessionId": "uuid",
  "shell": "bash|powershell|cmd"
}
```

### DELETE `/internal/agents/{endpointId}/sessions/{sessionId}`
Close a session (shell or desktop).

## Implementation

**Command Dispatcher Classes:**
- `ScriptCommandInternalController` — Routes script commands
- `SoftwareCommandInternalController` — Routes software commands
- `ProcessCommandInternalController` — Routes process commands
- `DesktopSessionInternalController` — Routes desktop session requests
- `ShellSessionInternalController` — Routes shell session requests

Each controller:
1. Validates the request payload
2. Looks up the endpoint UUID in AgentRegistry
3. Constructs a `GatewayCommand` protobuf message
4. Calls `observer.onNext(command)` to push onto the stream
5. Returns `202 Accepted` (or error if not found)

## Retry & Backoff

The calling service (e.g., commands-service) is responsible for retry logic:
- If the endpoint is offline (404), queue the command for later delivery
- When the endpoint reconnects, deliver all pending commands in order
- If delivery fails after N retries, mark the command as failed

Agent-hub does not implement retry; it only delivers immediately to connected agents.

## Metrics & Observability

- Counter: `agent.commands.dispatched` — Total commands pushed to agents
- Counter: `agent.commands.failed` — Commands that failed to dispatch (endpoint offline)
- Gauge: `agent.streams.active` — Current number of connected agents
- Timer: `agent.dispatch.latency` — Time from API call to command queued on stream

These metrics are exported to the Prometheus scrape endpoint at `localhost:8092/actuator/prometheus`.
