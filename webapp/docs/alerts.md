# Alerts & Real-Time Notifications (`src/pages/AlertsPage.jsx`, `src/hooks/useAlertStream.js`)

Threshold-based alert rules and the live Server-Sent Events stream that drives the notification bell.

### code
[`pages/AlertsPage.jsx`](../src/pages/AlertsPage.jsx):
	- `AlertsPage` - Lists alert rules (`useGetAlertRulesQuery`) and creates them (`useCreateAlertRuleMutation`) with a form covering metric type, comparison operator, threshold, sustained duration, and a group or endpoint target; deletes rules with `useDeleteAlertRuleMutation`.

[`hooks/useAlertStream.js`](../src/hooks/useAlertStream.js):
	- `useAlertStream` - Opens a single `EventSource` to `${API_BASE}/alerts/stream?token=<jwt>` while a token is present. Listens for `alert` events (dispatched to `addAlert`) and `notification` events (dispatched to `addNotif`), and closes the stream on cleanup.

[`components/NotificationBell.jsx`](../src/components/NotificationBell.jsx):
	- `NotificationBell` - Seeds the `alerts` slice from the REST `getAlerts('open')` query, calls `useAlertStream()` to go live, renders the dropdown, and acknowledges alerts.

### description
Alert rules are plain CRUD over the rule set — the page defines *when* an alert should fire (metric, operator, threshold, how long it must hold, and which group or endpoint it watches), and the backend evaluator does the firing. The interesting half is delivery. The bell first loads the currently open alerts over REST so it is correct on page load, then `useAlertStream` opens one `EventSource` and the feed becomes live: fired alerts and operational notifications (endpoint online/offline, script results, …) are pushed as named SSE events and folded straight into the `alerts` slice, which recomputes the badge count.

SSE is the right transport here because the flow is one-directional (server → browser) and `EventSource` reconnects on its own through proxies. As with the other streaming endpoints, the JWT travels as a `?token=` query parameter because `EventSource` cannot set headers; the gateway converts it back to a bearer token for validation. The slice that backs all of this is documented in [State Management](state.md), and the bell's placement in the shell is in [Application Shell & Navigation](layout.md).
</content>
