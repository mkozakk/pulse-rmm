# User stories

This document captures who uses Pulse RMM and what they need from it, organized by epic. It is the requirements catalogue, so it describes goals rather than implementation status; some epics here — notably the policy engine and remote wipe — are not built yet, and [plan.md](plan.md) is the honest record of what exists today. The permissions catalogue below, however, is the real access model the system enforces.

## The access model

Pulse RMM uses a permission-based model. Every capability maps to a named permission, and a role is simply a named bundle of permissions. Administrators can use the built-in roles, clone them, or assemble custom roles from the catalogue, and individual permissions can also be granted directly to a single user. Every permission can be scoped: held across the whole fleet, or narrowed to one or more endpoint groups. Granting `remote:desktop:control` fleet-wide is different from granting it only for `group:finance-laptops`, and because groups form a tree, a grant on a group covers its descendants too.

## Permissions catalogue

The permissions, grouped by area:

**Endpoint** — `endpoint:view` (inventory, metrics, processes, services), `endpoint:act` (kill processes, stop services, reboot), `endpoint:wipe` (issue a remote wipe), `endpoint:wipe:approve` (second-person approval for a wipe), `endpoint:structure:manage` (groups, moves, tags, and auto-tagging rules).

**Remote session** — `remote:shell`, `remote:desktop:view`, `remote:desktop:control`, `remote:file`, and `remote:unattended` (connect while no user is logged in).

**Scripting** — `script:run` (run from the approved library), `script:adhoc` (upload and run one-off scripts), `script:approve` (manage the approved library), `script:secret` (inject secrets without leaking them to logs).

**Software** — `software:view` (inventory) and `software:manage` (install, update, remove, patch, roll back).

**Policy** — `policy:view` (read policies and drift) and `policy:manage` (edit, assign, reconcile).

**Dashboards and alerts** — `dashboard:view`, `dashboard:manage`, and `alert:manage` (create and edit rules, acknowledge alerts).

**Enrolment** — `enrolment:manage` (create and revoke tokens, view failures).

**Identity and RBAC** — `identity:user:manage` (users and API keys), `identity:rbac:manage` (roles and direct grants), `identity:sso:manage` (SSO and MFA policy).

**Audit** — `audit:view` and `audit:export`.

**Integrations** — `integration:manage` (outbound webhook configuration).

**Agent and platform** — `agent:manage` (auto-update, canary, rollback) and `platform:manage` (platform settings).

## Default roles

Four roles ship by default. The **admin** has everything, and is the only role that holds the `identity:*` permissions, `platform:manage`, and `endpoint:wipe:approve` out of the box. The **senior technician** has the day-to-day operational permissions — all of `endpoint:*` except wipe approval, all of `remote:*`, scripting including ad-hoc and secrets, all of `software:*`, enrolment and alert management, dashboards, and read access to policy and audit — plus `endpoint:structure:manage` for group and tag housekeeping. The **junior technician** is deliberately narrower: view endpoints, view-only desktop, run approved scripts, view software and policy, view dashboards, and manage alerts. The **auditor** is read-only, with the audit permissions plus view access to endpoints, software, policy, and dashboards.

One stakeholder is not a Pulse role at all: the **endpoint user**, the person physically using the managed machine. Technicians have to account for them through consent prompts, the activity indicator, and the tray's "request help" button.

## Epics

**Enrolment and installation.** A holder of `enrolment:manage` needs to generate group-scoped invitation tokens so that enrolled endpoints land in the right place without reusing a static secret, and to revoke tokens and see enrolment failures with reasons so leaked tokens are useless and failed onboardings can be debugged. A senior technician wants both a one-liner install command, for onboarding over SSH without uploading files, and a double-click installer package for non-technical users.

**Monitoring and alerting.** A holder of `endpoint:view` wants live and historical CPU, memory, and disk metrics plus a process list, so they can diagnose performance complaints and judge whether a reboot is safe; `endpoint:act` extends that to killing runaway processes and rebooting. A holder of `alert:manage` wants threshold rules per group and the ability to acknowledge alerts, so a critical server crossing ninety-percent disk pages someone while routine noise stays muted.

**Remote access.** A holder of `remote:shell` wants a terminal on any in-scope endpoint for running diagnostics. A holder of `remote:desktop:control` wants to view and drive the desktop to walk a user through a problem, while `remote:desktop:view` gives an observer a session with no risk of accidental clicks. `remote:file` adds file transfer in both directions, and `remote:unattended` — kept separate precisely so most technicians cannot silently connect to unattended machines — allows sessions when no user is logged in. An admin may want to require a recorded reason and a consent prompt for sessions against sensitive groups.

**Software and patch management.** A holder of `software:view` wants a per-endpoint software inventory to answer licensing questions and spot vulnerable versions, and `software:manage` adds installing, updating, and removing software.

**Scripting and automation.** A holder of `script:adhoc` wants to run an ad-hoc script across one or many endpoints for bulk fixes, while a holder of only `script:run` is restricted to pre-approved scripts so they can do common tasks without shipping arbitrary code; `script:approve` lets a reviewer gate what enters the library. A holder of `script:secret` wants to pass credentials into a run without them appearing in logs, and every technician wants per-endpoint output and exit codes so they know which runs succeeded.

**Policy engine.** A holder of `policy:manage` wants to declare desired state — required apps, services, and firewall rules — assign it to groups, and trigger reconciliation so endpoints stay consistent; `policy:view` wants drift detection that explains *why* each endpoint is non-compliant, so root causes get fixed rather than papered over.

**Identity, RBAC, SSO, and organizations.** A holder of `identity:user:manage` registers technicians and assigns roles so each sees only what they need, and creates API keys scoped to specific permissions, groups, and organizations. A holder of `identity:rbac:manage` builds custom roles from the catalogue, clones and edits default roles, grants one-off time-bounded permissions directly to a user for incidents, and reviews who holds a given permission. A holder of `identity:sso:manage` enforces MFA and integrates SSO so stolen passwords are not enough and offboarding through the IdP is automatic. Every technician wants a session that times out on inactivity, and an MSP admin wants to create organizations, assign users to them, and have every permission evaluated per organization so one customer's technicians never see another's endpoints.

**Audit and accountability.** A holder of `audit:view` wants an append-only log of every mutating action — who, what, when, on which endpoint, and which permission was used — plus configuration history, to reconstruct incidents; `audit:export` adds CSV and JSON export for external reviewers; and an admin wants the storage to be tamper-evident so an insider cannot rewrite history.

**Remote wipe.** A holder of `endpoint:wipe` wants to issue a wipe so a lost laptop does not leak data, an admin wants every wipe to require a separate `endpoint:wipe:approve` holder to confirm so one compromised account cannot wipe the fleet, and the requester wants to watch the wipe's status as it progresses.

**API and integrations.** An admin wants a REST API covering every UI capability, authorized by the same permissions as the UI, so workflows can be automated, along with OpenAPI docs for generating client libraries. A holder of `integration:manage` wants signed outbound webhooks with retries and a dead-letter queue so events reach Jira or ServiceNow reliably.

**Agent auto-update.** A holder of `agent:manage` wants agents to update themselves through a canary rollout — one percent of the fleet, then ten, then a hundred — with automatic rollback on a failed health check, so a bad release cannot brick the fleet.

**Deployment and operations.** A platform engineer wants a podman-compose stack for local and small environments and Kubernetes manifests for production, so moving from dev to prod uses consistent container definitions, along with infrastructure provisioning and Prometheus/Grafana dashboards out of the box.

**Groups and tags.** A holder of `endpoint:structure:manage` wants to build a tree of groups to scope permissions and policies by organizational structure, move endpoints between groups to reflect reorganizations, and define auto-tagging rules based on endpoint attributes so common tags are not set by hand. Any technician wants to tag endpoints with `key=value` labels and filter dashboards, alerts, and policies by tag, so they can target endpoints across groups — every `env=prod` machine regardless of site.

**Endpoint user experience.** On an interactive desktop, the endpoint user wants a tray icon showing whether Pulse is running and whether a technician is connected, and a "request help" button with an optional message; the technician wants that request to raise an in-app notification carrying the endpoint, the user's name, and their message. Technicians also want to see when each endpoint last checked in and a reason if it is offline, so they can triage connectivity before opening a session — an endpoint is flagged offline after about ninety seconds of silence. On headless systems the tray does not apply, and those endpoints are managed through the webapp only.
