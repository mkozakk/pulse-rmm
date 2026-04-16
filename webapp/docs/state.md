# State Management (`src/store/`)

The Redux store. It holds three reducers: the authentication token, the live alerts feed, and the RTK Query cache.

### code
[`store/store.js`](../src/store/store.js):
	- `store` - `configureStore` wiring three reducers — `auth`, `alerts`, and the `pulseApi` cache reducer — and concatenating the RTK Query middleware (required for caching, invalidation, and polling).

[`store/authSlice.js`](../src/store/authSlice.js):
	- `auth` slice - `{ token, initialized }`. See [Authentication & Session](auth.md) for the reducers (`setCredentials`, `clearCredentials`, `setInitialized`).

[`store/alertsSlice.js`](../src/store/alertsSlice.js):
	- `alerts` slice - `{ openAlerts, count, notifs }`. Backs the notification bell.
	- `seedAlerts` - Replaces `openAlerts` with the initial REST fetch and recomputes the badge `count`.
	- `addAlert` / `removeAlert` - Push a newly fired alert onto the list, or drop one when acknowledged; both keep `count` in sync.
	- `addNotif` / `dismissNotif` - Add or remove a transient operational notification (endpoint online/offline, script result, …) delivered over SSE.

### description
State is split by ownership. Server state — endpoints, scripts, software, audit, and everything else fetched over REST — lives entirely in the RTK Query cache and is never duplicated into a hand-written slice; pages read it through generated hooks. Only genuinely client-side concerns get their own slices: the current access token (`auth`) and the live alert feed (`alerts`).

The `alerts` slice exists because notifications arrive from two directions and must be merged into one badge. The initial set of open alerts is loaded once over REST and `seedAlerts`'d into the store, after which the SSE stream pushes new alerts (`addAlert`) and operational notifications (`addNotif`) as they happen. The slice keeps a single `count` — open alerts plus undismissed notifications — recomputed on every mutation so the bell badge is always correct. See [Alerts & Real-Time Notifications](alerts.md) for the stream that feeds it.
</content>
