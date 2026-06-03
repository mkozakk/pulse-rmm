# Pulse RMM - Development Plan

each sprint is ~2 weeks. sprints build on each other - do not skip ahead. the rule: a sprint is done only when its acceptance criteria all pass, not when code is written.

**Status: All sprints (0-18) are complete and merged to main.**

---

## sprint 0 - project foundation

**requirements**
- monorepo structure with all components scaffolded
- local dev environment via podman-compose (dependencies + app services)
- shared proto contract files compiled and generating stubs
- ci pipeline building all components on push

**acceptance criteria**
- `podman compose up` starts postgres 16, redis 7, rabbitmq 3, minio, backend services, and webapp - all healthy
- each spring boot module starts and responds to `GET /actuator/health` with 200
- go agent compiles (`go build ./...`) and exits cleanly with no panics
- react app renders on localhost:5173
- `buf generate` compiles proto files and produces java + go stubs without errors
- github actions (or equivalent) runs on every push and reports pass/fail

**technical approach**
- repo layout:
  ```
  pulse-rmm/
    agent/              go module
    backend/
      pom.xml           multi-module parent
      common/           shared proto-generated classes, exceptions, utils
      enrolment-service/
      metric-service/
      api-gateway/
    webapp/             react + vite
    proto/              .proto files (source of truth)
    deploy/
      compose.yaml      local dev stack (dependencies + app services)
    docs/
  ```
- `proto/` managed by buf; `buf.gen.yaml` generates java into `backend/common/src/generated` and go into `agent/gen/`
- `compose.yaml` mounts named volumes for postgres and minio so data survives restarts
- each spring boot service: spring-boot-starter-web, actuator, no business logic yet
- ci: one workflow - checkout → java build (`mvn verify -pl <module>`) → go build → vite build

---

## sprint 1 - identity & authentication (mvp subset)

**requirements** *(epic 7 - trimmed)*
- admin can register with username + password
- admin can log in and receive a jwt
- jwt expires after a configurable inactivity window
- invalid credentials return 401

**acceptance criteria**
- `POST /api/auth/register` (bootstrap only - remove or protect later) creates a user with bcrypt-hashed password
- `POST /api/auth/login` returns a signed jwt on valid credentials, 401 otherwise
- jwt contains user id and a `roles` claim; expiry configurable in application.yaml
- testcontainers integration test: starts real postgres, runs flyway, calls login endpoint, checks jwt
- no rbac enforcement yet - that comes in sprint 6

**technical approach**
- spring security with local `UserDetailsService`; no oauth2 yet
- jwt via `jjwt` library; hs256 signed with secret from environment variable
- flyway migration `V001__users.sql`: `users(id uuid pk, username varchar unique, password_hash varchar, created_at timestamptz)`
- do not store jwt in localstorage on the frontend - keep in memory (redux store). use a short-lived access token + httponly refresh cookie pattern from the start; changing this later is painful

---

## sprint 2 - enrolment

**requirements** *(epic 1)*
- authenticated admin creates an invitation token scoped to a group
- agent reads token from env/flag, contacts backend, receives a stable endpoint uuid
- enrolled endpoint appears in `GET /api/endpoints`

**acceptance criteria**
- `POST /api/enrolment/tokens` (requires auth) creates a token record in db scoped to a group
- agent calls `Enrol` grpc with the token + its ed25519 public key → receives endpoint uuid
- running enrolment a second time from the same endpoint does not create a duplicate (idempotent by public key)
- `GET /api/endpoints` lists the enrolled endpoint with id, hostname, group, status=online
- expired or revoked token returns grpc status `UNAUTHENTICATED`
- testcontainers integration test covers the full grpc enrol call

**technical approach**
- new db migrations: `groups(id, name, parent_id)`, `enrolment_tokens(id, group_id, expires_at, revoked)`, `endpoints(id uuid, hostname, os, arch, group_id, public_key, enrolled_at, last_seen)`
- enrolment-service exposes the grpc `Enrol` rpc; api-gateway routes rest calls
- agent: on first run, generate ed25519 keypair with `golang.org/x/crypto/ed25519`, store private key in a local file (e.g. `/var/lib/pulse-agent/key.pem`), call `Enrol`, persist received endpoint uuid alongside the key
- grpc channel: plaintext for now (add mtls in a hardening sprint later)
- do not implement installer packaging yet - agent runs via `PULSE_TOKEN=xxx ./agent` for now

---

## sprint 3 - heartbeat & metrics

**requirements** *(epic 2 - monitoring only)*
- agent sends a heartbeat every 30s
- agent collects cpu, ram, disk, os uptime and pushes them every 30s
- backend stores metrics in timescaledb
- endpoint marked offline after 3 missed pushes (~90s)

**acceptance criteria**
- `Heartbeat` grpc updates `endpoints.last_seen` in db
- `PushMetrics` grpc stores a row per metric type per push in the timescale hypertable
- `GET /api/endpoints/{id}/metrics?from=&to=&type=cpu` returns a time-series array
- an endpoint that stops sending for >90s gets `status=offline` (set by a scheduled job)
- load test: simulate 200 agents (goroutine-based test client) pushing metrics concurrently - all rows land without errors
- compression policy on the hypertable set to compress after 7 days

**technical approach**
- proto: `rpc Heartbeat(HeartbeatRequest) returns (HeartbeatOk)`, `rpc PushMetrics(MetricBatch) returns (Ack)`
- `MetricBatch`: endpoint_id, list of `{type, value, collected_at}`
- agent uses `github.com/shirou/gopsutil/v3` for cpu/ram/disk; goroutines for heartbeat + collect+push loops
- metric-service writes to timescale hypertable: `metric_samples(endpoint_id, type, value, sampled_at)` - `sampled_at` is the partition key
- timescale compression + retention policy: 30 days (set up in flyway migration)
- `@Scheduled(fixedDelay = 30_000)` in metric-service marks endpoints offline when `last_seen < now() - 90s`

---

## sprint 4 - webapp: login + endpoint list + metrics view

**requirements** *(client for sprints 1–3)*
- login page with username + password
- endpoint list showing all enrolled endpoints with online/offline badge
- per-endpoint view: live metric charts (cpu, ram, disk) + historical chart

**acceptance criteria**
- login submits to `POST /api/auth/login`, stores jwt, redirects to `/endpoints`
- failed login shows error message
- endpoint list auto-refreshes online/offline status every 30s
- metric chart shows last 1h by default; user can switch to 6h / 24h / 7d
- charts update every 30s without full page reload
- works in latest chrome, firefox, edge, safari (manual smoke test)
- unauthenticated requests to any page redirect to login

**technical approach**
- react + vite + redux toolkit (auth slice for token + user state)
- rtk query for data fetching with 30s `pollingInterval`; no websockets yet
- chart library: recharts (simpler api than chart.js for line charts)
- protected routes: wrapper component checks auth state, redirects if missing
- api-gateway handles cors; set `credentials: include` on fetch for the refresh-cookie flow

---

## sprint 5 - remote shell

**requirements** *(epic 3 - shell only)*
- senior technician opens a terminal on any online endpoint from the webapp
- input and output stream bidirectionally in real time
- session closes cleanly on user exit or tab close

**acceptance criteria**
- clicking "open terminal" on an endpoint opens a terminal pane in the webapp
- commands execute on the endpoint; stdout/stderr appear within 200ms
- `exit` or ctrl+d closes the session on both sides with no orphaned process
- attempting to open shell without `remote:shell` permission returns 403
- shell works on windows (powershell or cmd) and linux (bash)

**technical approach**
- proto: `rpc Shell(stream ShellInput) returns (stream ShellOutput)` - bidirectional stream
- backend proxies: webapp connects via websocket to api-gateway, which holds the grpc stream to the agent. backend acts as a bridge, not a terminal emulator
- agent (linux): `github.com/creack/pty` to spawn bash with a pty; forwards pty output to grpc stream
- agent (windows): `github.com/UserExistsError/conpty` (conpty api) to spawn powershell
- webapp: `xterm.js` for terminal rendering; resizes forwarded as a `TerminalResize` message in the stream
- session lifecycle: agent closes pty → sends `EOF` message → webapp terminal shows disconnected

---

## sprint 6 - full rbac & permission enforcement

**requirements** *(epic 7 - full)*
- permissions, roles, and assignments stored in db
- every api endpoint and grpc rpc checks the caller's permissions
- admin can create custom roles, assign permissions, assign roles to users
- permissions can be scoped to a group

**acceptance criteria**
- all api endpoints return 403 if the caller lacks the required permission
- a user with `remote:shell` scoped to `group:servers` cannot open a shell on an endpoint in `group:laptops`
- admin can create a role, add `endpoint:view` to it, assign to a user - that user then sees endpoints but gets 403 on `/shell`
- direct permission grant overrides the role's grants (union, no denies)
- integration tests cover: correct access, missing permission, wrong group scope

**technical approach**
- migrations: `permissions(id, name)`, `roles(id, name)`, `role_permissions(role_id, permission_id, group_scope_id nullable)`, `user_roles(user_id, role_id)`, `user_permissions(user_id, permission_id, group_scope_id nullable, expires_at nullable)` - seed catalog from `user_stories.md`
- permission evaluation service: union of all permissions from roles + direct grants, filtered by group scope
- spring security: custom `AuthenticationToken` holding the evaluated permission set; `@PreAuthorize("hasPermission('remote:shell', #endpointId)")` using a custom `PermissionEvaluator` that checks scope
- grpc interceptor does the same check on the server-side for agent-facing calls
- cache evaluated permissions per user in redis (ttl = 60s) to avoid db hit on every request

---

## sprint 7 - groups & tag management

**requirements** *(epic 13)*
- admin creates and manages the group tree
- endpoints can be moved between groups
- technician tags endpoints manually; auto-tagging rules applied at enrolment and on demand
- endpoint list and dashboards can be filtered by tag

**acceptance criteria**
- `POST /api/groups` creates a group; `parent_id` is optional (null = root)
- `PUT /api/endpoints/{id}/group` moves endpoint to another group; permission scoping updates immediately
- `PUT /api/endpoints/{id}/tags` sets tags; `GET /api/endpoints?tag=env%3Dprod` returns correct subset
- auto-tag rule: "if os = windows → tag `os=windows`" runs at enrolment and when manually triggered
- cycles in the group tree are rejected (validated on write)

**technical approach**
- groups table: `parent_id` self-referential fk; max depth enforced at write (e.g. 5 levels)
- `endpoint_tags(endpoint_id, key, value)`; unique constraint on `(endpoint_id, key)` - last write wins per key
- `tag_rules(id, condition_field, condition_value, tag_key, tag_value)` evaluated at enrolment and via `POST /api/tag-rules/evaluate`
- group tree queries use recursive cte (`with recursive`) in postgres for descendant lookups
- tag filter on `GET /api/endpoints`: `?tag=env%3Dprod&tag=site%3Dwarsaw` is an AND query

---

## sprint 8 - software inventory & patch management

**requirements** *(epic 4)*
- agent reports installed software list per endpoint
- technician can install, update, or remove a package from the webapp
- software list reflects changes after an action completes

**acceptance criteria**
- agent scans and pushes software list on start and every 10 minutes
- `GET /api/endpoints/{id}/software` returns current list
- `POST /api/endpoints/{id}/software/install` with `{name, version}` delivers a command to the agent, executes, acks result
- action result (success / failed + output) visible in webapp within 30s of completion
- test: install a known package on a real test vm; verify it appears in the software list

**technical approach**
- agent: runs `choco list` / `apt list --installed` / `dnf list installed` depending on os; parses output into `SoftwareItem{name, version, source}`
- proto: `rpc PushSoftwareList(SoftwareList) returns (Ack)`, command type `INSTALL/UPDATE/REMOVE` in the existing command delivery pattern
- command delivery pattern (used in scripting too): backend inserts `commands(id, endpoint_id, type, payload, status=pending)` → agent polls (or receives via open grpc stream) → executes → calls `AckCommand(id, exit_code, output)`
- redis pub/sub: backend notifies webapp via server-sent events (sse) when a command ack arrives

---

## sprint 9 - scripting & automation

**requirements** *(epic 5)*
- technician uploads and runs ad-hoc scripts on one or many endpoints
- junior technician can only run from the approved library
- script output + exit code visible per endpoint
- secrets injected without appearing in logs

**acceptance criteria**
- `POST /api/scripts` creates a script (requires `script:adhoc`)
- `POST /api/script-library/{id}/approve` moves it to the approved library (requires `script:approve`)
- `POST /api/scripts/{id}/run` with a list of endpoint ids fans out execution; results collectable via `GET /api/script-runs/{run_id}/results`
- a user with only `script:run` gets 403 when targeting a non-library script
- secrets passed in `POST /api/scripts/{id}/run` body are encrypted before storing in db and not logged anywhere
- bulk run of 100 endpoints completes (all acks received) within 60s

**technical approach**
- `scripts(id, name, body, approved_at)`, `script_runs(id, script_id, initiated_by)`, `script_run_results(run_id, endpoint_id, exit_code, output, executed_at)`
- secrets: encrypted at rest with a kek (key-encryption-key) from secrets manager; decrypted only when building the command payload; payload transmitted to agent over the (future) mtls channel
- bulk fan-out via rabbitmq: `ScriptRunRequested` event → consumers per endpoint in parallel
- agent executes script body in a subprocess (powershell on windows, bash on linux); captures stdout+stderr; calls `AckCommand`

---

## sprint 10 - remote desktop (webrtc)

*this is the hardest sprint. budget extra time.*

**requirements** *(epic 3 - desktop)*
- senior technician views and controls an endpoint's desktop from the browser
- file transfer over the same session
- session works behind nat via turn relay
- wayland: view-only (unattended blocked, prompt required per session)

**acceptance criteria**
- desktop video appears in the browser within 5s of opening the session
- mouse moves and keyboard input are injected on the endpoint
- file upload (drag-and-drop) and download work over the webrtc data channel
- session works when both sides are behind nat (coturn relay)
- on wayland: capture only starts after the user on the endpoint accepts the pipewire portal prompt
- `remote:desktop:control` required; `remote:desktop:view` gives view-only (input injector disabled)

**technical approach**
- signaling: `rpc Signal(stream SignalMessage) returns (stream SignalMessage)` via the existing grpc control stream - exchange sdp offer/answer and ice candidates
- agent (screen capture): desktop duplication api on windows, xshm on x11, pipewire portal on wayland
- agent (encoding): offer h264 hw encode first (nvenc/amf/d3d11 on win, vaapi on linux); fall back to vp9 sw on linux (royalty-free). see codec negotiation in `plan.md`
- agent (input): `sendinput` on windows, `xtest` on x11, `libevdev` on wayland
- relay: deploy `coturn` container; credentials generated per session (short-lived turncredentials)
- file transfer: webrtc data channel, chunked binary protocol
- webapp: `RTCPeerConnection` api, `<video>` element, mouse/keyboard event capture forwarded over the input data channel
- wayland unattended limitation is documented in `plan.md` - no workaround; just surface a clear error if the portal prompt is dismissed

---

## sprint 11 - in-app alerting

**requirements** *(epic 2 - alerts)*
- admin creates threshold-based alert rules per group or tag selector
- in-app webapp notification fires when a rule triggers
- alerts can be acknowledged

**acceptance criteria**
- `POST /api/alert-rules` creates a rule: `{metric_type, operator, threshold, duration_secs, target: {type: group|tag, value}}` 
- alert evaluator runs every 30s; triggers an alert event when condition holds for the full duration
- unacknowledged alert appears in the webapp notification bell within 30s of triggering
- `POST /api/alerts/{id}/ack` marks it acknowledged
- same rule does not re-fire until the condition clears and re-triggers

**technical approach**
- `alert_rules(id, metric_type, operator, threshold, duration_secs, target_type, target_value)`, `alert_events(id, rule_id, endpoint_id, triggered_at, acked_at)`
- evaluator: `@Scheduled` queries timescale - for each active rule, check if the condition held for `duration_secs` using a windowed aggregate. if yes and no open event exists → insert alert_event; publish to rabbitmq
- sse endpoint `GET /api/notifications/stream`: webapp subscribes; alert consumer sends down the sse channel when a new event arrives
- de-duplication: one open alert per rule+endpoint; re-arm only after ack + condition clear

---

## sprint 12 - policy engine

**requirements** *(epic 6)*
- admin declares a yaml policy (required apps, required services)
- agent reports compliance state
- drift visible in webapp; reconciliation can be triggered

**acceptance criteria**
- `POST /api/policies` with yaml body: valid yaml accepted; invalid yaml returns 400 with parse error
- policy assigned to a group → each endpoint in that group evaluated
- agent reports whether each requirement is met (based on software list + service list already pushed)
- non-compliant endpoints listed in `GET /api/policies/{id}/compliance` with reason
- `POST /api/policies/{id}/reconcile` generates install/start commands for drifted requirements and delivers them
- reconciliation result visible in compliance view after ack

**technical approach**
- policy yaml schema: `{require: {apps: [{name, version_gte}], services: [{name, state: running}]}}`
- `policies(id, name, yaml_body)`, `policy_assignments(policy_id, group_id)`, `endpoint_compliance(endpoint_id, policy_id, status, last_evaluated_at, reasons jsonb)`
- compliance evaluation: backend compares the policy requirements against `software_list` and `service_list` (already stored by sprint 8); updates `endpoint_compliance`
- reconcile: generate `INSTALL` and `SERVICE_START` commands using the same command delivery pattern from sprint 8
- re-evaluate on every software list push from agent

---

## sprint 13 - audit log

**requirements** *(epic 8)*
- every mutating action logged with who, what, when, on which endpoint, which permission used
- audit view in webapp (paginated)
- csv / json export

**acceptance criteria**
- every `POST`, `PUT`, `DELETE` api call and every grpc command creates an audit record
- `GET /api/audit?from=&to=&user=&endpoint=` returns paginated results
- `GET /api/audit/export?format=csv` streams a downloadable file
- no audit record can be deleted via the api (403 on any delete attempt)
- integration test: make 3 api calls, verify 3 audit records exist

**technical approach**
- `audit_events(id, user_id, permission_used, action, endpoint_id, payload jsonb, created_at)` - no update/delete endpoint exists in the api
- spring aop: `@Around` advice on `@Auditable` service methods publishes an `AuditEvent` to rabbitmq audit topic
- audit-service consumes and persists; decoupled so a slow audit write doesn't block the api response
- export: streaming `StreamingResponseBody` writes csv rows as they're fetched (avoid loading all records into memory)

---

## sprint 14 - remote wipe

**requirements** *(epic 9)*
- admin issues a remote wipe command
- a second admin must approve before the wipe executes
- wipe status tracked

**acceptance criteria**
- `POST /api/endpoints/{id}/wipe` (requires `endpoint:wipe`) creates a pending request; does not execute yet
- a different user with `endpoint:wipe:approve` calls `POST /api/endpoints/{id}/wipe/{request_id}/approve` to confirm
- the same user cannot both request and approve (backend enforces)
- agent receives wipe command and begins execution; status transitions to in-progress → completed
- completed wipe: endpoint deleted from fleet or marked wiped

**technical approach**
- `wipe_requests(id, endpoint_id, requested_by, approved_by, status, created_at, executed_at)`
- approval check: `requested_by != approved_by`; backend returns 409 if same user attempts approval
- wipe execution on agent: schedule a task (schtask / systemd timer) to run on next system start or immediately if possible. windows: `cipher /w:c:\ && format c: /fs:ntfs /q /y`. linux: `shred + rm -rf /`. note: this is destructive and irreversible - test only against disposable vms

---

## sprint 15 - agent autoupdate & canary rollout

**requirements** *(epic 11)*
- new agent version published to s3
- agent checks for updates and self-updates
- canary rollout: 1% → 10% → 100% of the fleet (random sample)
- automatic rollback if health check fails post-update

**acceptance criteria**
- publishing a new version artifact to s3 triggers a canary rollout plan
- 1% of endpoints receive the update first; admin must manually advance to 10%, then 100% (or configure auto-advance)
- agent performs its own health check post-update (can it start and reach backend?); rolls back to previous binary if it fails within 60s
- rollout progress visible in webapp

**technical approach**
- `agent_versions(id, version, artifact_s3_key, published_at)`, `rollout_plans(id, version_id, status, cohort_pct)`, `endpoint_update_assignments(endpoint_id, version_id, status)`
- agent polls `GET /api/updates/check` with current version; backend returns `no_update` or a signed s3 presigned url
- cohort assignment: hash(endpoint_id) mod 100 < cohort_pct → in canary; deterministic so re-checking is idempotent
- agent update flow: download new binary → verify sha256 hash → rename old binary to `.prev` → swap → restart → health check → if fail: restore `.prev` → call `ReportUpdateFailed`

---

## sprint 16 - webhooks & integrations

**requirements** *(epic 10)*
- outbound webhooks for key events (alert fired, agent offline, session opened)
- signed deliveries (hmac-sha256)
- retries with dead-letter queue

**acceptance criteria**
- `POST /api/webhooks` registers a url + list of event types
- alert fires → webhook post to registered url within 10s; payload signed with `X-Pulse-Signature` header
- failed delivery (non-2xx or timeout) retried 3 times with exponential backoff
- after 3 failures, event lands in dead-letter queue visible in admin
- integration test: mock http server verifies signature + payload

**technical approach**
- `webhooks(id, url, secret, event_types[])`, `webhook_deliveries(id, webhook_id, event_type, payload, status, attempts, next_retry_at)`
- rabbitmq consumer: `WebhookDispatcher` picks up events, calls the url, records result
- resilience4j retry on the http call (3 attempts, exponential backoff)
- dead-letter: if all retries exhausted, set `status=dead_letter`; admin ui shows these
- signature: `hmac-sha256(secret, raw_request_body)` - same pattern as github webhooks

---

## sprint 17 - endpoint user experience (tray)

**requirements** *(epic 14)*
- tray icon shows connection status and active session indicator
- "request help" sends an in-app notification to all online technicians with the endpoint and message
- applies to windows + linux gui sessions only

**acceptance criteria**
- tray icon visible in windows system tray and linux appindicator (ubuntu/gnome tested)
- icon changes when a technician opens a session
- clicking "request help" opens a text box; submitting sends to backend; webapp shows notification
- on headless systems (no display): tray code does not crash; logs a message and exits gracefully

**technical approach**
- go library: `github.com/getlantern/systray` (cross-platform, windows + linux)
- agent detects headless: check `DISPLAY` / `WAYLAND_DISPLAY` env vars on linux; on windows check `GetSystemMetrics(SM_CXSCREEN)`
- help request: `POST /api/help-requests` from agent using its jwt; backend publishes to sse channel; webapp notification bell shows it
- active session indicator: backend sends a `SESSION_STARTED` / `SESSION_ENDED` command down the grpc control stream; agent updates tray icon state

---

## sprint 18 - sso & mfa

**requirements** *(epic 7 - sso + mfa)*
- totp-based mfa enforced per user
- oidc sso with an external idp (test with keycloak)
- disabling user in idp revokes access automatically

**acceptance criteria**
- `POST /api/auth/mfa/enroll` returns a totp qr code; subsequent logins require the otp
- invalid totp returns 401
- oidc login flow: redirect → idp → callback → jwt issued
- a user disabled in keycloak cannot obtain a new jwt (idp login fails); existing tokens expire naturally within their ttl
- test: local keycloak in compose.yaml, automated oidc login test

**technical approach**
- totp: `dev.samstevens.totp` library for secret generation and validation; store encrypted totp secret in users table
- oidc: `spring-security-oauth2-client` with keycloak as the registered client; on successful oidc callback, look up or create the local user by `sub` claim
- mfa is enforced at the jwt issuance step: if user has mfa enrolled, require otp before issuing the token
- saml is explicitly out of scope - oidc covers the same use case with less implementation complexity

---

## cross-sprint hardening tasks

schedule these as short tasks within sprints when they become relevant, not as separate sprints:

| task | do it when |
|------|------------|
| mtls between services and agents | after sprint 2 (enrolment working) |
| grpc tls for agent channel | after sprint 2 |
| redis-based permission cache | after sprint 6 |
| timescale compression + retention policy | sprint 3 |
| rate limiting via bucket4j | after sprint 4 (webapp live) |
| secret encryption (kek pattern) | sprint 9 (scripting) |
| structured json logging → loki | any sprint, early is better |
| opentelemetry traces | after sprint 5 (first multi-service call) |
| api openapi docs via springdoc | after sprint 4 |
| endpoint agent installer packaging (exe, deb, rpm) | after sprint 15 (autoupdate) |
