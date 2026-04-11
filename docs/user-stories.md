# Pulse RMM - user stories

## identity model

pulse-rmm uses a **permission-based** access model. every capability maps to a named permission. a **role** is just a named bundle of permissions. admins can use the built-in roles, clone them, or build custom roles from the catalog. individual permissions can also be granted directly to a user.

every permission can be **scoped**: global (whole fleet) or narrowed to one or more endpoint groups. e.g. `remote:desktop:control` may be granted fleet-wide, or only for `group:finance-laptops`.

### permissions catalog

**endpoint**
- `endpoint:view` - inventory, live + historical metrics, processes, services
- `endpoint:act` - kill processes, stop services, reboot
- `endpoint:wipe` - issue a remote wipe
- `endpoint:wipe:approve` - second-person approval for a wipe
- `endpoint:structure:manage` - create / rename / delete groups, move endpoints, manage tags and auto-tagging rules

**remote session**
- `remote:shell` - terminal access
- `remote:desktop:view` - view-only remote desktop
- `remote:desktop:control` - full mouse / keyboard control
- `remote:file` - file transfer both directions
- `remote:unattended` - connect while the endpoint user is logged out

**scripting**
- `script:run` - run scripts from the approved library
- `script:adhoc` - upload and run one-off scripts
- `script:approve` - manage the approved library
- `script:secret` - inject secrets into a run without leaking them to logs

**software & patching**
- `software:view` - installed-software inventory
- `software:manage` - install / update / remove / os & third-party patches / rollback

**policy engine**
- `policy:view` - read policies and drift
- `policy:manage` - edit, assign to groups or tag selectors, trigger reconcile

**dashboards & alerts**
- `dashboard:view`
- `dashboard:manage` - create / share dashboards
- `alert:manage` - rule create/edit (targeting a group or tag selector) + ack

**enrolment**
- `enrolment:manage` - create/revoke tokens, view failures

**identity & rbac**
- `identity:user:manage` - create / edit / disable users, api keys
- `identity:rbac:manage` - roles and direct permission grants
- `identity:sso:manage` - sso + mfa policy

**audit**
- `audit:view` - logs, config history
- `audit:export` - csv / json

**integrations**
- `integration:manage` - outbound webhook configuration (api keys are managed under `identity:user:manage`)

**agent & platform**
- `agent:manage` - autoupdate config, canary rollout, rollback
- `platform:manage` - platform-level settings

### default roles

- **admin** - everything. the only role with `identity:*`, `platform:manage`, and `endpoint:wipe:approve` by default.
- **senior technician** - `endpoint:*` (minus `wipe:approve`), `remote:*`, `script:run` + `script:adhoc` + `script:secret`, `software:*`, `policy:view`, `enrolment:manage`, `alert:manage`, `dashboard:*`, `audit:view`. includes `endpoint:structure:manage` for day-to-day group / tag housekeeping.
- **junior technician** - `endpoint:view`, `remote:desktop:view`, `script:run`, `software:view`, `policy:view`, `dashboard:view`, `alert:manage`.
- **auditor** - `audit:*`, `endpoint:view`, `software:view`, `policy:view`, `dashboard:view`. read-only.

**endpoint user** is a stakeholder, not a pulse-rmm role - the person physically using the endpoint. technicians must consider them (consent prompts, activity status, tray "request help").

any permission can be **scoped to endpoint groups**. a junior with `remote:desktop:view` scoped to `group:retail-stores` can only view retail endpoints.

stories use the form: *as a \<role or permission holder\>, i want \<goal\>, so that \<reason\>*.

---

## epic 1: enrolment & installation

- as a holder of `enrolment:manage`, i want to generate an invitation token scoped to a specific group, so that enrolled endpoints land in the right place without reusing a static secret.
- as a **senior technician**, i want a one-liner bash / powershell install command, so that i can onboard endpoints over ssh without file uploads.
- as a **senior technician**, i want a signed .exe / .deb / .rpm, so that a non-technical user can install the agent by double-clicking.
- as a holder of `enrolment:manage`, i want to revoke tokens and see enrolment failures with reason codes, so that leaked tokens are useless and i can debug why an endpoint didn't appear.

## epic 2: monitoring & alerting

- as a holder of `endpoint:view`, i want live and historical cpu / ram / disk / gpu metrics and a list of running processes, so that i can diagnose performance complaints and judge whether a reboot is safe.
- as a holder of `endpoint:act`, i want to kill runaway processes and reboot endpoints, so that i can fix things without visiting the machine.
- as a holder of `alert:manage`, i want threshold-based alert rules per group and the ability to ack alerts, so that i'm notified when a critical server crosses 90% disk and can mute noise.
- as a holder of `dashboard:manage`, i want to build and share a dashboard, so that the team watches the same signals during an incident.

## epic 3: remote access

- as a holder of `remote:shell`, i want to open a shell on any in-scope endpoint, so that i can run diagnostic commands remotely.
- as a holder of `remote:desktop:control`, i want to view and control the desktop, so that i can walk a user through a problem.
- as a holder of `remote:desktop:view` (without control), i want a view-only session, so that i can observe without risking accidental clicks.
- as a holder of `remote:file`, i want to transfer files both directions, so that i can deliver a config or collect a log without email.
- as a holder of `remote:unattended`, i want sessions to work when the endpoint user is logged out, so that i can fix things out of hours. this is a separate permission so most techs can't silently connect to unattended machines.
- as an **admin**, i want to require a recorded reason + consent prompt for sessions against a given group, so that sensitive endpoints get extra oversight.

## epic 4: software & patch management

- as a holder of `software:view`, i want an inventory of installed software per endpoint, so that i can answer license questions and spot vulnerable versions.
- as a holder of `software:manage`, i want to install / update / remove software, schedule os updates in maintenance windows, manage third-party patches (chrome, acrobat, java), and roll back bad ones.

## epic 5: scripting & automation

- as a holder of `script:adhoc`, i want to run an ad-hoc script on one or many endpoints, so that i can perform bulk fixes.
- as a holder of `script:run` (without `:adhoc`), i want to run pre-approved scripts only, so that i can do common tasks without shipping arbitrary code.
- as a holder of `script:approve`, i want to review scripts before they enter the library, so that users with only `script:run` can't execute dangerous code.
- as a holder of `script:secret`, i want to pass credentials into a script without them appearing in logs.
- as a **technician**, i want per-endpoint script output and exit codes, so that i know which runs succeeded.

## epic 6: policy engine

- as a holder of `policy:manage`, i want to declare policies in yaml (required apps, services, firewall rules), assign them to groups, and trigger reconciliation, so that endpoints stay in a consistent configuration.
- as a holder of `policy:view`, i want drift detection with a *why* for each non-compliant endpoint, so that i can fix root causes rather than just re-applying.

## epic 7: identity, rbac, sso & organizations

- as a holder of `identity:user:manage`, i want to register new technicians and assign them roles, so that they only see what they need.
- as a holder of `identity:rbac:manage`, i want to **create custom roles** by selecting permissions from the catalog, so that i can express team-specific job functions.
- as a holder of `identity:rbac:manage`, i want to **clone a default role and edit the clone**, so that i don't rebuild from scratch.
- as a holder of `identity:rbac:manage`, i want to **grant a one-off permission directly to a user** (e.g. temporary `remote:desktop:control` for an incident), **scoped to specific groups and organizations** and **time-bounded**, so that elevated access doesn't outlive the need.
- as a holder of `identity:rbac:manage`, i want to see which users hold a given permission (directly or via a role), so that access reviews are possible.
- as a holder of `identity:sso:manage`, i want to enforce mfa and integrate sso (saml / oidc), so that stolen passwords aren't enough and offboarding via the idp is automatic.
- as a holder of `identity:user:manage`, i want to create api keys **scoped to specific permissions, groups, and organizations**, so that integrations can do one thing without broader access.
- as a **technician**, i want my session to time out after inactivity, so that an unattended browser isn't a risk.
- as an **msp admin**, i want to create and manage separate organizations, so that i can isolate customers' endpoints and users within a single pulse-rmm deployment.
- as an **msp admin**, i want to assign users to specific organizations, so that each customer's technicians only see their own endpoints, policies, and scripts.
- as a holder of `identity:rbac:manage`, i want all permissions to be evaluated per organization, so that a user with `endpoint:view` in org A cannot see org B's endpoints.

## epic 8: audit & accountability

- as a holder of `audit:view`, i want an append-only log of every mutating action (who, what, when, on which endpoint, **which permission was used**) plus config history, so that i can reconstruct incidents.
- as a holder of `audit:export`, i want csv / json export, so that i can hand records to external reviewers.
- as an **admin**, i want tamper-evident storage of audit records, so that an insider can't rewrite history.

## epic 9: remote wipe

- as a holder of `endpoint:wipe`, i want to issue a remote wipe, so that a lost laptop doesn't leak data.
- as an **admin**, i want wipes to require a separate holder of `endpoint:wipe:approve` to confirm, so that one compromised technician can't wipe the fleet.
- as a holder of `endpoint:wipe`, i want to see wipe status (pending / in-progress / completed / failed).

## epic 10: api & integrations

- as an **admin**, i want a rest api covering every ui capability, so that workflows can be automated. api calls are authorised by the same permissions as the ui (scoped on the api key).
- as a holder of `integration:manage`, i want signed outbound webhooks with retries and a dead-letter queue, so that events reach jira / servicenow reliably.
- as an **admin**, i want openapi docs, so that client libraries can be generated.

## epic 11: agent autoupdate

- as a holder of `agent:manage`, i want agents to autoupdate with a canary rollout (1% → 10% → 100% of the fleet, sampled randomly) and automatic rollback on failed health checks, so that a bad release doesn't brick the fleet.

## epic 12: deployment & operations

- as a **platform engineer**, i want a podman-compose file for local dev and small environments and kubernetes manifests for production, so that we can move from dev to prod with consistent container definitions.
- as a **platform engineer**, i want terraform modules for the underlying infra (db, object storage) and prometheus + grafana dashboards out of the box, so that environments are reproducible and observable without extra work.

## epic 13: groups & tags

- as a holder of `endpoint:structure:manage`, i want to create a tree of groups (e.g. `hq > sales > laptops`), so that i can scope permissions and policies by organisational structure.
- as a holder of `endpoint:structure:manage`, i want to move an endpoint between groups, so that i can reflect reorganisations or re-purposed machines.
- as a **technician**, i want to tag endpoints with `key=value` labels (manually or via installer parameters), so that i can target them independently of group structure.
- as a holder of `endpoint:structure:manage`, i want auto-tagging rules based on endpoint attributes (os, hostname pattern, ip range), so that common tags don't have to be set by hand on every new endpoint.
- as a **technician**, i want to filter dashboards, alerts, and policies by tag, so that i can target endpoints across groups (e.g. all `env=prod` regardless of site).

## epic 14: endpoint user experience

note: the tray-based stories below assume an interactive gui session. on headless systems (linux servers, logged-out windows sessions) the tray is unavailable and "request help" does not apply - technicians interact with those endpoints only through the webapp.

- as an **endpoint user**, i want a tray icon that shows whether pulse-rmm is running and whether a technician is currently connected, so that i know when i'm being supervised.
- as an **endpoint user**, i want a "request help" button in the tray with an optional message box, so that i can flag a problem without finding a technician's contact details.
- as a **technician**, i want "request help" to raise an in-app webapp notification with the endpoint id, the requesting user's name, and their message, so that i can respond quickly.
- as a **technician**, i want to see when each endpoint last checked in and a reason code if it's offline (network, suspended, agent crashed, uninstalled), so that i can triage connectivity issues before opening a session. an endpoint is flagged offline after 3 missed pushes (~90s).
