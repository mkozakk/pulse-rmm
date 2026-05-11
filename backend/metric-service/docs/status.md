# Status Monitoring (`application/`)

Determines endpoint connectivity states asynchronously based on historical telemetry patterns.

### code
**Background Jobs**
[`OfflineMarkerJob.java`](../src/main/java/dev/pulsermm/metric/application/OfflineMarkerJob.java):
	- An asynchronous, scheduled task that periodically scans the time-series database to identify agents that have failed to report a heartbeat within a designated timeframe, subsequently updating their global status to "Offline."

### description
In a distributed fleet spanning multiple corporate networks, physical endpoints do not explicitly notify the central server when they lose power, enter sleep mode, or experience a network cable disconnection; they simply go silent. Therefore, the system must infer their operational status based on the presence or absence of data. Agents are configured to emit a lightweight heartbeat payload at regular intervals via gRPC. As long as the Metric Service receives these pings and records them via the ingestion pipeline, the endpoint is considered functionally online. 

However, the system requires a mechanism to actively flag disconnected machines so administrators are aware of outages. The `OfflineMarkerJob` fulfills this requirement by running continuously in the background on a scheduled, independent thread. It queries the TimescaleDB hypertables, looking for any endpoint UUID whose most recent heartbeat timestamp is older than a configurable safety threshold (e.g., three minutes). When it identifies these stale records, it flags the endpoint as offline. This asynchronous approach ensures that the primary database ingestion paths remain fully dedicated to handling incoming high-velocity data streams without being slowed down by constant, synchronous status evaluations.
