# Pulse RMM Webapp

The React single-page application that technicians and administrators use to operate the Pulse RMM fleet. It talks only to the API Gateway over REST, WebSocket, and Server-Sent Events; it never reaches the backend services or agents directly. Authentication is delegated to Keycloak (OIDC + PKCE), and all server state is fetched, cached, and invalidated through a single RTK Query client.

## Directory Structure

```text
webapp/
├── docs/                   # Full documentation of all webapp features
├── src/
│   ├── api/                # The RTK Query client - every REST endpoint and cache tag
│   ├── components/         # Reusable UI: app shell, charts, panels, route guard
│   ├── hooks/              # Cross-cutting React hooks (alert SSE stream, WebRTC desktop session)
│   ├── pages/              # One component per route, mounted by the router in App.jsx
│   ├── store/              # Redux store and slices (auth token, live alerts)
│   ├── keycloak.js         # Keycloak client singleton (realm, client id, OIDC config)
│   ├── main.jsx            # Entry point: initialises Keycloak, then mounts the app
│   ├── App.jsx             # Router and route table
│   └── index.css           # Global styles and design tokens
├── index.html              # Vite HTML host
├── vite.config.js          # Vite build/dev configuration
├── Dockerfile              # Build into static assets served by nginx
└── package.json            # Dependencies and scripts (dev, build, test)
```

## Key Responsibilities

- **Authenticated console** — Logs the operator in through Keycloak, keeps the access token fresh, and gates every route behind a valid session.
- **Fleet operations UI** — Endpoint inventory, live metrics, processes, scripts, and software management across the whole fleet.
- **Remote access** — Browser-based remote terminal (xterm + WebSocket) and remote desktop (WebRTC) with bidirectional file transfer.
- **Monitoring & notifications** — Alert rules, a live notification stream over SSE, and the immutable audit log.
- **Administration** — Enrolment tokens, agent-version distribution, outbound webhooks, users, roles, and organizations.
- **Single data layer** — One RTK Query client owns all server communication, request authorization, caching, and cache invalidation.

## Features & Internal Documentation

* **[Bootstrap, Routing & Error Handling](docs/bootstrap.md)** - How the app boots, mounts the router, guards routes, and contains render errors (`main.jsx`, `App.jsx`, `ProtectedRoute`, `ErrorBoundary`).
* **[Authentication & Session](docs/auth.md)** - Keycloak OIDC + PKCE login, silent token refresh, the auth slice, and global-admin vs. org-scoped detection.
* **[API Client & Caching](docs/api-client.md)** - The RTK Query client: base query, bearer-token injection, the tag-based cache, and the full endpoint catalogue.
* **[State Management](docs/state.md)** - The Redux store and its slices (`auth`, `alerts`) alongside the RTK Query cache reducer.
* **[Application Shell & Navigation](docs/layout.md)** - The persistent sidebar layout, grouped navigation, collapse state, and the notification bell.
* **[Endpoints, Metrics & Processes](docs/endpoints.md)** - The endpoint list, the detail view with live metric charts and system info, and the remote process manager.
* **[Remote Terminal](docs/remote-terminal.md)** - The xterm.js terminal bridged to the agent shell over a binary WebSocket framing protocol.
* **[Remote Desktop & File Transfer](docs/remote-desktop.md)** - The WebRTC desktop session hook, input forwarding, and the data-channel file transfer panel.
* **[File Browser](docs/files.md)** - Directory listing, upload, and authenticated download against the endpoint filesystem.
* **[Scripts & Software](docs/automation.md)** - The script library with approval and fan-out execution, and per-endpoint software inventory and package actions.
* **[Enrolment & Agent Versions](docs/enrolment.md)** - Enrolment-token minting, groups, tag rules, and the agent-version upload and rollout UI.
* **[Alerts & Real-Time Notifications](docs/alerts.md)** - Threshold alert rules and the live SSE notification stream feeding the bell.
* **[Audit Log](docs/audit.md)** - The filterable, paginated, exportable view over the immutable audit trail.
* **[Webhooks](docs/webhooks.md)** - Webhook subscriptions, event-type selection, and per-delivery inspection with the dead-letter view.
* **[Users & Organizations](docs/administration.md)** - User and role management plus the global-admin organization console.

## Routing & Page Map

Every route except the redirects is wrapped in `ProtectedRoute`, which blocks rendering until Keycloak has resolved and a token is present. Unauthenticated visitors are bounced to the Keycloak login; `/` and unknown paths redirect to `/endpoints`.

| Route | Page | Purpose |
|---|---|---|
| `/endpoints` | `EndpointsPage` | Fleet inventory with search and status/OS filters |
| `/endpoints/:id` | `EndpointDetailPage` | Live metrics, system info, and quick actions for one endpoint |
| `/endpoints/:id/shell` | `TerminalPage` | Remote terminal |
| `/endpoints/:id/desktop` | `DesktopPage` | Remote desktop |
| `/endpoints/:id/files` | `FilesPage` | File browser |
| `/endpoints/:id/processes` | `EndpointProcessesPage` | Live process manager |
| `/enrolment` | `EnrolmentPage` | Tokens, groups, tag rules |
| `/scripts` | `ScriptsPage` | Script library and execution |
| `/software` | `SoftwarePage` | Software inventory and package actions |
| `/alerts` | `AlertsPage` | Alert rules |
| `/audit` | `AuditPage` | Audit log |
| `/agent-versions` | `AgentVersionsPage` | Agent build distribution |
| `/webhooks`, `/webhooks/:id` | `WebhooksPage`, `WebhookDetailPage` | Webhook subscriptions and deliveries |
| `/users` | `UsersPage` | User and role management |
| `/organizations` | `OrganizationsPage` | Organization management (global admin only) |

## Data & Real-Time Flow

The webapp uses three transports to the API Gateway:

1. **REST** — All CRUD and command traffic goes through the RTK Query client (`src/api/pulseApi.js`). Each request refreshes the Keycloak token if it is within 30 s of expiry, then attaches it as a bearer token.
2. **WebSocket** — The remote terminal (`/ws/shell/{id}`) and the remote-desktop signaling channel (`/ws/sessions/{id}/signal`) open authenticated WebSockets directly to the gateway, which bridges them to the agent.
3. **Server-Sent Events** — A single `EventSource` (`/api/alerts/stream`) pushes fired alerts and operational notifications into the Redux `alerts` slice, which drives the notification bell in real time.

Because the `EventSource` and WebSocket APIs cannot set custom headers, the JWT is passed as a `?token=` query parameter on those URLs; REST calls use the standard `Authorization` header.

## Development

```bash
# Install dependencies
npm install

# Run the dev server (Vite, hot reload) against a running backend
npm run dev

# Type-free unit/component tests (Vitest + Testing Library)
npm test

# Production build into dist/
npm run build
```

Configuration is read from Vite environment variables (`.env`, `.env.local`):

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_BASE` | `http://localhost:8080/api` | REST base URL |
| `VITE_WS_BASE` | `ws://localhost:8080` | WebSocket base URL (shell, desktop signaling) |
| `VITE_KEYCLOAK_URL` | `http://localhost:8080/auth` | Keycloak base URL |

In production the app is built to static assets and served by nginx, which also reverse-proxies `/api`, the WebSocket upgrades, and the SSE stream to the API Gateway. See [deploy/nginx.conf](../deploy/nginx.conf).

## Related Components

- **[API Gateway](../backend/api-gateway/README.md)** — The only backend the webapp talks to; authenticates requests and routes them to the owning service, and bridges shell/desktop WebSockets.
- **[RBAC Service](../backend/rbac-service/README.md)** — Backs the users, roles, and organizations screens; Keycloak is the identity provider the webapp logs in against.
- **[Alert Service](../backend/alert-service/README.md)** — Produces the SSE stream consumed by the notification bell.
- **[Agent](../agent/README.md)** — The endpoint software the webapp drives through the gateway (shell, desktop, scripts, software, processes).
</content>
</invoke>
