# Application Shell & Navigation (`src/components/AppShell.jsx`)

The persistent chrome around every page: the grouped sidebar, the page header, and the notification bell.

### code
[`components/AppShell.jsx`](../src/components/AppShell.jsx):
	- `NAV` - The static navigation model, grouped into sections (**Fleet**, **Automation**, **Monitoring**, **Administration**), each item carrying a route, label, and a `lucide-react` icon.
	- `AppShell` - The layout wrapper. Takes `title`, `subtitle`, and `actions` props and renders the sidebar, a header bar, the notification bell, and the page body. Adds the **Organizations** item to the Administration section only when `keycloak.tokenParsed?.org_id` is absent (global admin).
	- `toggleNav` - Collapses or expands the sidebar and persists the choice in `localStorage` (`navCollapsed`) so it survives reloads.

[`components/NotificationBell.jsx`](../src/components/NotificationBell.jsx):
	- `NotificationBell` - The bell button and dropdown. Seeds the `alerts` slice from `useGetAlertsQuery('open')`, subscribes to the live feed via `useAlertStream()`, renders open alerts and operational notifications, and acknowledges an alert through `useAckAlertMutation`.

### description
`AppShell` is the single layout every page renders inside, so navigation, header, and notifications are defined once. Pages pass their `title`, optional `subtitle`, and any header `actions` (buttons such as "New script") as props, and otherwise just supply their body — they do not redraw the sidebar.

The navigation is a static model rendered with `NavLink`, which marks the active route automatically. The only dynamic element is multi-tenancy: the Organizations entry appears solely for global admins, mirroring the same `org_id`-based distinction the rest of the app uses (see [Authentication & Session](auth.md)). Collapse state is a pure UI preference, so it lives in `localStorage` rather than Redux. The notification bell is mounted in the shell so it is visible on every screen; it owns the live alert subscription and badge described in [Alerts & Real-Time Notifications](alerts.md).
</content>
