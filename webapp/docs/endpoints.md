# Endpoints, Metrics & Processes (`src/pages/Endpoints*`, `src/components/*Chart*`)

The fleet inventory, the per-endpoint detail view with live metric charts and system info, and the remote process manager.

### code
[`pages/EndpointsPage.jsx`](../src/pages/EndpointsPage.jsx):
	- `EndpointsPage` - The inventory table from `useGetEndpointsQuery` (polled every 30 s). Client-side search by hostname/id plus status and OS filters; each row links to the detail view and offers quick shell/desktop actions.

[`pages/EndpointDetailPage.jsx`](../src/pages/EndpointDetailPage.jsx):
	- `EndpointDetailPage` - The detail view. Loads the endpoint, its system info, and time-ranged metric series (1h/6h/24h/7d), and renders CPU, memory, disk, per-core, and network charts plus quick links to shell, desktop, files, and processes.
	- `useMetric` - Helper that calls `useGetMetricsQuery` for one metric type (`cpu`, `disk.used_bytes`, `disk.free_bytes`, `disk.total_bytes`, `net.rx_bytes`, `net.tx_bytes`) over the selected window, re-fetching on a ticking clock.

[`components/MetricChart.jsx`](../src/components/MetricChart.jsx) / [`PerCoreCpuChart.jsx`](../src/components/PerCoreCpuChart.jsx) / [`NetworkChart.jsx`](../src/components/NetworkChart.jsx) / [`PerDiskTable.jsx`](../src/components/PerDiskTable.jsx):
	- Chart components - Recharts-based renderers for a single percentage series, per-core CPU lines keyed off the `core` label, RX/TX throughput computed as deltas between samples, and the per-mount disk usage table.

[`components/SystemInfoPanel.jsx`](../src/components/SystemInfoPanel.jsx):
	- `SystemInfoPanel` - Static hardware/OS facts (CPU model and core counts, RAM, disks) with a `formatBytes` helper reused by the disk and network views.

[`pages/EndpointProcessesPage.jsx`](../src/pages/EndpointProcessesPage.jsx):
	- `EndpointProcessesPage` - The live process manager. Triggers a fresh scan with `useRefreshProcessesMutation`, reads the latest snapshot via `useGetLatestProcessesQuery`, supports search, and kills a process with `useKillProcessMutation`.

### description
The endpoint screens are the operational heart of the console. The list view is kept current by polling rather than manual refresh, and all filtering is done client-side over the already-fetched set so it stays responsive. The detail view composes several independent metric queries — one per series — each scoped to the chosen time window and re-issued on a tick, which is why those queries opt out of the RTK Query cache (`keepUnusedDataFor: 0`): a metric chart should never show a stale window.

Charts are thin presentation over Recharts. The network chart is the one with real logic: throughput is not stored directly, so it derives a rate by differencing consecutive cumulative byte counters and dividing by the sample interval. Per-core CPU and per-disk views fan a single labelled metric stream out into one line or row per `core`/`mount` label. The process manager is request-driven rather than streamed: refreshing asks the agent (through the gateway) for a new snapshot, and killing a process issues a command and lets the next snapshot reflect the result.
</content>
