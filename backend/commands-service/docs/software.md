# Software Management (`api/`, `application/`, `domain/`, `infrastructure/`)

Manages endpoint software inventory (discovery and scanning), and orchestrates software package operations (install, update, remove) via the command delivery pattern.

### code

**Data Transfer Objects (DTOs)**
[`InstallRequest.java`](../src/main/java/dev/pulsermm/commands/software/api/InstallRequest.java) & [`RemoveRequest.java`](../src/main/java/dev/pulsermm/commands/software/api/RemoveRequest.java):
	- Capture the administrator's intent to install, update, or remove a package, specifying the package name, optional app ID (for winget support), and target version.
[`SoftwareItemResponse.java`](../src/main/java/dev/pulsermm/commands/software/api/SoftwareItemResponse.java):
	- Exposes discovered software inventory on an endpoint: name, app ID, installed version, available updates, MS Store status, and source (winget, registry, etc.).
[`CommandResponse.java`](../src/main/java/dev/pulsermm/commands/software/api/SoftwareController.java#L118-L123):
	- Returns the command ID and status (pending) immediately after queueing a package operation, allowing the frontend to track progress.

**Controllers**
[`SoftwareController.java`](../src/main/java/dev/pulsermm/commands/software/api/SoftwareController.java):
	- Public REST API for authenticated users: list installed software, install, update, remove packages.
	- Endpoints:
		- `GET /api/endpoints/{endpointId}/software` - list installed software on endpoint (HTTP 200)
		- `POST /api/endpoints/{endpointId}/software/install` - queue package installation (HTTP 201)
		- `POST /api/endpoints/{endpointId}/software/update` - queue package update (HTTP 201)
		- `POST /api/endpoints/{endpointId}/software/remove` - queue package removal (HTTP 201)
[`SoftwareInternalController.java`](../src/main/java/dev/pulsermm/commands/software/api/SoftwareInternalController.java):
	- Internal API for agent acknowledgments:
		- `POST /internal/commands/{commandId}/ack` - agent reports package operation completion or failure.
		- `POST /internal/software-list` - agent pushes discovered software inventory.

**Application Logic**
[`SoftwareService.java`](../src/main/java/dev/pulsermm/commands/software/application/SoftwareService.java):
	- Core orchestrator: receives package requests, persists command records, dispatches to agent via `AgentHubClient`, and records command acknowledgments.
	- Key methods:
		- `storeSoftwareList()` - replaces endpoint's software inventory from agent scan.
		- `getSoftwareList()` - fetches current software inventory for an endpoint.
		- `createCommand()` - creates a pending command and dispatches it to the agent.
		- `ackCommand()` - marks a command complete with exit code and output, publishes domain event.

**Domain Entities & Repositories**
[`SoftwareItem.java`](../src/main/java/dev/pulsermm/commands/software/domain/SoftwareItem.java) & [`SoftwareItemRepository.java`](../src/main/java/dev/pulsermm/commands/software/infrastructure/SoftwareItemRepository.java):
	- Represents a single installed package: name, app ID, version, available update, MS Store flag, source, and last scan time.
	- Unique constraint: (endpoint_id, app_id) ensures no duplicate packages per endpoint.
[`SoftwareCommand.java`](../src/main/java/dev/pulsermm/commands/software/domain/SoftwareCommand.java) & [`SoftwareCommandRepository.java`](../src/main/java/dev/pulsermm/commands/software/infrastructure/SoftwareCommandRepository.java):
	- Represents a queued or completed package operation: action (install/update/remove), package name, app ID, status (pending/completed/failed), exit code, and output.
	- Status transitions: pending → completed (exitCode=0) or pending → failed (exitCode != 0).

### description

Software management follows the command delivery pattern shared with scripts and processes. When an administrator requests a package installation, update, or removal, the `SoftwareService` creates a `SoftwareCommand` record in the database and immediately returns the command ID. The service then calls `AgentHubClient.dispatchSoftwareCommand()` to send the request to the agent over the gRPC stream.

The agent executes the operation on the endpoint (using winget, apt, yum, or other OS-native package managers) and reports back with an exit code and output. The API Gateway translates this response into a webhook callback to `POST /internal/commands/{commandId}/ack`, which updates the command status and publishes a domain event for audit logging.

Software items are discovered passively: the agent periodically scans the endpoint and pushes the inventory via `POST /internal/software-list`. The service replaces the endpoint's entire software list atomically (delete old, insert new) to ensure consistency.

**Winget support (V003 migration):** Modern Windows endpoints support winget package management, which distinguishes packages by App ID (e.g., `Google.Chrome`) rather than display name. The schema added `app_id`, `update_to` (available update), and `is_store` (Microsoft Store package) columns to track this metadata. The unique constraint was updated from (endpoint_id, name) to (endpoint_id, app_id) to avoid duplicate records.

### API endpoints

```
GET    /api/endpoints/{endpointId}/software                201 []SoftwareItemResponse
POST   /api/endpoints/{endpointId}/software/install        201 CommandResponse {id, status}
POST   /api/endpoints/{endpointId}/software/update         201 CommandResponse {id, status}
POST   /api/endpoints/{endpointId}/software/remove         201 CommandResponse {id, status}
POST   /internal/commands/{commandId}/ack                  204 (no body)
POST   /internal/software-list                             204 (no body)
```

### database schema

**software.software_items** - discovered endpoint packages
```sql
id                  UUID PRIMARY KEY
endpoint_id         UUID NOT NULL (FK)
name                VARCHAR(256) NOT NULL - package display name
app_id              VARCHAR(256) - unique package identifier (e.g., Google.Chrome for winget)
version             VARCHAR(128) NOT NULL - installed version
update_to           VARCHAR(128) - available update version
is_store            BOOLEAN DEFAULT false - true if from MS Store
source              VARCHAR(64) NOT NULL - discovery source (winget, registry, apt, etc.)
last_scanned_at     TIMESTAMPTZ NOT NULL DEFAULT now()
UNIQUE(endpoint_id, app_id)
INDEX on (endpoint_id)
```

**software.software_commands** - pending and completed package operations
```sql
id                  UUID PRIMARY KEY
endpoint_id         UUID NOT NULL (FK)
action              VARCHAR(32) NOT NULL - install, update, or remove
package_name        VARCHAR(256) NOT NULL - package to operate on
app_id              VARCHAR(256) - package app ID for winget
package_version     VARCHAR(128) - target version (for install/update)
status              VARCHAR(32) NOT NULL DEFAULT 'pending' - pending, completed, or failed
exit_code           INTEGER - 0 for success, non-zero for failure
output              TEXT - stdout/stderr from the operation
created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
completed_at        TIMESTAMPTZ - set when status transitions to terminal state
INDEX on (endpoint_id, status)
INDEX on (id)
```

### dependencies

- Spring Boot 3.3.x
- Spring Data JPA (repository abstraction)
- PostgreSQL 16 + Flyway (schema migrations)
- AgentHubClient (internal gRPC dispatch)
