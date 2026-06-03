# Process Management (`api/`, `application/`, `domain/`, `infrastructure/`)

Manages endpoint process discovery and lifecycle control (listing active processes, killing processes by PID) via the command delivery pattern.

### code

**Data Transfer Objects (DTOs)**
[`ProcessSnapshotResponse.java`](../src/main/java/dev/pulsermm/commands/processes/api/dto/ProcessSnapshotResponse.java):
	- Exposes a completed process list snapshot: command ID, endpoint ID, status (PENDING, COMPLETED, FAILED), JSON process array, and completion timestamp.
[`RefreshResponse.java`](../src/main/java/dev/pulsermm/commands/processes/api/dto/RefreshResponse.java):
	- Returns the command ID immediately after requesting a fresh process list, allowing the frontend to poll for progress.
[`AckRequest.java`](../src/main/java/dev/pulsermm/commands/processes/api/dto/AckRequest.java):
	- Internal webhook payload: exit code and output from agent process operations.

**Controller**
[`ProcessController.java`](../src/main/java/dev/pulsermm/commands/processes/api/controller/ProcessController.java):
	- Public REST API for authenticated users and internal agent callbacks:
		- `POST /api/endpoints/{endpointId}/processes/refresh` - request a fresh process list snapshot (HTTP 201).
		- `GET /api/endpoints/{endpointId}/processes/latest` - fetch the latest completed process snapshot (HTTP 200).
		- `POST /api/endpoints/{endpointId}/processes/{pid}/kill` - queue process termination by PID (HTTP 202).
		- `POST /api/processes/commands/{commandId}/ack` - internal callback for process list completion.
		- `POST /api/processes/kill-commands/{commandId}/ack` - internal callback for process kill completion.

**Application Logic**
[`ProcessService.java`](../src/main/java/dev/pulsermm/commands/processes/application/ProcessService.java):
	- Core orchestrator: manages process discovery and termination commands.
	- Key methods:
		- `refresh()` - creates a ProcessSnapshot record and dispatches list request to agent.
		- `kill()` - creates a ProcessKillCommand record and dispatches kill request to agent.
		- `latestCompleted()` - fetches the most recent completed snapshot for an endpoint.
		- `ackListProcesses()` - marks snapshot complete with parsed process JSON or error.
		- `ackKillProcess()` - marks kill command complete with exit code and error message.
	- Uses `TransactionSynchronization` to ensure agent dispatch happens after database commit.

**Domain Entities & Repositories**
[`ProcessSnapshot.java`](../src/main/java/dev/pulsermm/commands/processes/domain/ProcessSnapshot.java) & [`ProcessSnapshotRepository.java`](../src/main/java/dev/pulsermm/commands/processes/infrastructure/ProcessSnapshotRepository.java):
	- Represents a point-in-time capture of running processes on an endpoint: status (PENDING, COMPLETED, FAILED), JSON process list, error message (if failed), requester, and timestamps.
	- Query method: `findTopByEndpointIdAndStatusOrderByCompletedAtDesc()` for fetching latest completed snapshot.
[`ProcessKillCommand.java`](../src/main/java/dev/pulsermm/commands/processes/domain/ProcessKillCommand.java) & [`ProcessKillCommandRepository.java`](../src/main/java/dev/pulsermm/commands/processes/infrastructure/ProcessKillCommandRepository.java):
	- Represents a request to terminate a process by PID: endpoint ID, PID, status (PENDING, COMPLETED, FAILED), error message, requester, and timestamps.

### description

Process management enables real-time visibility and control over running processes on endpoints. The `ProcessController` exposes two operations: refreshing the process list and killing a process by PID.

When an administrator requests a process list, the `ProcessService` creates a `ProcessSnapshot` record with status PENDING and immediately returns the command ID. It then calls `AgentHubClient.dispatchListProcesses()` with a callback URL pointing to `/api/processes/commands/{commandId}/ack`. The agent collects the list of running processes and sends it back; the Gateway invokes the callback, and the service updates the snapshot with the process JSON and sets status to COMPLETED.

Similarly, when killing a process, the service creates a `ProcessKillCommand`, dispatches `AgentHubClient.dispatchKillProcess()` with the PID and callback URL, and awaits the agent's response at `/api/processes/kill-commands/{commandId}/ack`.

The service uses `TransactionSynchronization.registerSynchronization()` to ensure agent dispatch happens only after the database commit succeeds, preventing race conditions.

Endpoints can fetch the latest completed process snapshot via `GET /api/endpoints/{endpointId}/processes/latest`, which returns the most recent COMPLETED snapshot ordered by completion time.

### API endpoints

```
POST   /api/endpoints/{endpointId}/processes/refresh                201 RefreshResponse {commandId}
GET    /api/endpoints/{endpointId}/processes/latest                 200 ProcessSnapshotResponse
POST   /api/endpoints/{endpointId}/processes/{pid}/kill             202 RefreshResponse {commandId}
POST   /api/processes/commands/{commandId}/ack                      204 (no body)
POST   /api/processes/kill-commands/{commandId}/ack                 204 (no body)
```

### database schema

**scripts.process_snapshots** - point-in-time captures of running processes
```sql
id                  UUID PRIMARY KEY DEFAULT gen_random_uuid()
endpoint_id         UUID NOT NULL (FK)
status              VARCHAR(20) NOT NULL - PENDING, COMPLETED, or FAILED
processes           JSONB - array of process objects (command, PID, memory, CPU, etc.)
error               TEXT - error message if status is FAILED
requested_by        UUID - user who initiated the refresh
created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
completed_at        TIMESTAMPTZ - set when agent responds
INDEX on (endpoint_id, created_at DESC)
```

**scripts.process_kill_commands** - requests to terminate processes
```sql
id                  UUID PRIMARY KEY DEFAULT gen_random_uuid()
endpoint_id         UUID NOT NULL (FK)
pid                 INTEGER NOT NULL - process ID to terminate
status              VARCHAR(20) NOT NULL - PENDING, COMPLETED, or FAILED
error               TEXT - error message if status is FAILED
requested_by        UUID - user who initiated the kill
created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
completed_at        TIMESTAMPTZ - set when agent responds
```

### dependencies

- Spring Boot 3.3.x
- Spring Data JPA (repository abstraction)
- PostgreSQL 16 with JSONB support (process snapshots stored as JSON)
- Flyway (schema migrations)
- AgentHubClient (internal gRPC dispatch)
- Jackson ObjectMapper (JSON parsing of process snapshots)
