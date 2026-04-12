# API & Configuration (`api/`, `/`)

Manages application entry points, security boundaries, and REST interfaces for ingestion and querying telemetry.

### code

**Core Application**
[`MetricApplication.java`](../src/main/java/dev/pulsermm/metric/MetricApplication.java):
	- Spring Boot entry point that bootstraps the server, enables dependency injection, and initializes database connection pools.

**Public Query API**
[`MetricController.java`](../src/main/java/dev/pulsermm/metric/api/MetricController.java):
	- REST endpoints secured with JWT Bearer token. Allows authenticated users (webapp, dashboards) to query historical metrics and static system information for rendering charts and reports.
	- `GET /api/endpoints/{id}/metrics` - Query metric samples by type and time range, with optional label filters.
	- `GET /api/endpoints/{id}/system-info` - Retrieve cached hardware info (CPU model, cores, RAM, disk/NIC inventory).

**Internal Ingestion API**
[`InternalMetricController.java`](../src/main/java/dev/pulsermm/metric/api/InternalMetricController.java):
	- Unauthenticated REST endpoints under `/internal/` prefix for agents (routed via API Gateway) to push telemetry. No JWT check; security relies on network isolation.
	- `POST /internal/metrics` - Push metric samples (type, value, sampled_at, labels).
	- `POST /internal/heartbeat` - Report endpoint is online.
	- `POST /internal/system-info` - Upload static hardware inventory.

**Security & Documentation**
[`SecurityConfig.java`](../src/main/java/dev/pulsermm/metric/api/SecurityConfig.java):
	- Enforces JWT validation on `/api/*` endpoints (public query API) while allowing unauthenticated `/internal/*` (ingestion). Zero-trust: validates tokens locally even though API Gateway already authenticated them.
[`OpenApiConfig.java`](../src/main/java/dev/pulsermm/metric/api/OpenApiConfig.java):
	- Generates OpenAPI/Swagger documentation for the public query API endpoints.

### description

The service exposes two distinct API surfaces: **internal ingestion** and **public queries**.

**Ingestion:** Agents push raw data to `/internal/*` endpoints without authentication (secured by network boundary). The `InternalMetricController` validates JSON payloads and forwards them to `MetricIngestionService`. Metrics land in the `metric_samples` hypertable, system info in `endpoint_system_info`, and heartbeats update endpoint status and publish events.

**Queries:** The webapp and dashboards call `/api/endpoints/{id}/metrics` (with JWT) to fetch historical data for charts. The `MetricController` validates the Bearer token, applies time/type/label filters, and returns metric points as an array of `{sampledAt, value, labels}` records. System info is fetched separately and includes hardware inventory (CPU, RAM, disks, NICs).

Both flows share `MetricIngestionService`, which orchestrates database operations and domain event publishing.
