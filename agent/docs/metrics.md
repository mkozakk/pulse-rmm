# Metrics & Heartbeat (`internal/metrics/`)

Responsible for hardware telemetry collection and connection tracking.

### code
[`client.go`](../internal/metrics/client.go):
	- `Client` - Wraps the gRPC connections to the backend, maintaining dedicated network streams for telemetry and heartbeats.
	- `NewClient` - Initializes the gRPC dial configuration with required transport credentials to establish the initial secure connection.
	- `Heartbeat` - Transmits a minimal payload to the server at regular intervals to maintain the endpoint's active online status.
	- `PushMetrics` - Transmits batched hardware telemetry over gRPC for ingestion into the TimescaleDB backend.

[`collector.go`](../internal/metrics/collector.go):
	- `Sample` - Represents a single telemetry data point, strictly typing the metric name, float value, and collection timestamp.
	- `Collect` - Aggregates CPU and memory utilization data by sampling local hardware sensors before transmission.

[`collector_linux.go`](../internal/metrics/collector_linux.go) & [`collector_windows.go`](../internal/metrics/collector_windows.go):
	- `diskUsage` - Calculates primary partition storage capacity using OS-specific file system queries.

### description
The backend relies on continuous updates from the agent to populate dashboard charts and determine if a device has gone offline. This module fulfills those requirements through two distinct periodic tasks executed via a dedicated gRPC client. In the main application loop, `context.Context` and `time.Ticker` are used to schedule these tasks asynchronously via goroutines. The `Heartbeat` mechanism performs a lightweight remote procedure call triggered every 30 seconds, pinging the server to update the endpoint's last seen timestamp. The `Collect` routine handles the actual telemetry gathering. It leverages the external `gopsutil` library to query the operating system's hardware sensors, extracting the current utilization percentages for the CPU and physical memory. Disk space is calculated using platform-specific fallback files due to differences in mount points between Unix and Windows. These readings are packaged into a slice of `Sample` data, stamped with the current time, and passed to `PushMetrics` where they are marshalled into protobuf messages and transmitted to the metric service.
