# Telemetry Ingestion (`application/`, `domain/`, `infrastructure/`)

Handles the high-volume intake, normalization, and optimized storage of agent telemetry and hardware performance data.

### code

**REST Ingestion Controller**
[`InternalMetricController.java`](../src/main/java/dev/pulsermm/metric/api/InternalMetricController.java):
	- Exposes three internal REST endpoints under `/internal/` prefix that agents (routed via API Gateway) use to push metrics, report heartbeats, and upload system information. Validates UUID format and handles basic error recovery.

**Application Logic**
[`MetricIngestionService.java`](../src/main/java/dev/pulsermm/metric/application/MetricIngestionService.java):
	- Core service handling metrics ingestion, heartbeat updates, and system info storage. Orchestrates database inserts via JdbcTemplate and publishes domain events (e.g., `endpoint.online`, `endpoint.offline`) when endpoint status changes.

**Domain Entities**
[`EndpointHeartbeat.java`](../src/main/java/dev/pulsermm/metric/domain/EndpointHeartbeat.java):
	- Lightweight domain entity representing endpoint connectivity state: endpoint UUID, last seen timestamp, and status (online/offline).

**Infrastructure Repositories**
[`EndpointHeartbeatRepository.java`](../src/main/java/dev/pulsermm/metric/infrastructure/EndpointHeartbeatRepository.java):
	- Interfaces with PostgreSQL to manage endpoint heartbeat records. Provides queries like `findOnlineWithLastSeenBefore()` and bulk updates (`markOfflineWhere()`) for status transitions.

### description

Agents continuously report operational metrics, heartbeats, and hardware information to maintain fleet visibility. The `InternalMetricController` receives these three types of data via internal REST endpoints:

- **POST /internal/metrics** - Agents push metric samples (CPU, memory, disk, network) with type, value, timestamp, and optional labels.
- **POST /internal/heartbeat** - Lightweight ping to mark the endpoint as online. Triggers `endpoint.online` event if endpoint was previously offline.
- **POST /internal/system-info** - Static hardware inventory (CPU model, cores, RAM, disks, NICs) reported once on agent startup or hardware change.

The `MetricIngestionService` processes these payloads: validates endpoint UUID, normalizes timestamps, converts labels/disks/NICs to JSON, and inserts rows directly via JdbcTemplate for efficiency. Metric samples populate the `metric_samples` hypertable (time-series, partitioned by TimescaleDB). System info is upserted into `endpoint_system_info` (one row per endpoint). Heartbeats update the `endpoint_heartbeat` table and optionally publish events.

This REST-based approach (vs. gRPC) keeps the implementation simple and avoids protobuf versioning complexity for sprint 2–3. The API Gateway routes agent traffic to `/internal/*` paths without authentication, trusting the internal network boundary.
