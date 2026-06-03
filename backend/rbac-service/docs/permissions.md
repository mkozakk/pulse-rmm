# Permissions Catalog

This document outlines the complete list of system permissions managed by the Identity Service. These permissions are seeded into the database via Flyway migrations (specifically `V004__rbac_seed.sql`).

## Endpoint
- `endpoint:view` - inventory, live + historical metrics, processes, services
- `endpoint:act` - kill processes, stop services, reboot
- `endpoint:wipe` - issue a remote wipe
- `endpoint:wipe:approve` - second-person approval for a wipe
- `endpoint:structure:manage` - create / rename / delete groups, move endpoints, manage tags and auto-tagging rules

## Remote Session
- `remote:shell` - terminal access
- `remote:desktop:view` - view-only remote desktop
- `remote:desktop:control` - full mouse / keyboard control
- `remote:file` - file transfer both directions
- `remote:unattended` - connect while the endpoint user is logged out

## Scripting
- `script:run` - run scripts from the approved library
- `script:adhoc` - upload and run one-off scripts
- `script:approve` - manage the approved library
- `script:secret` - inject secrets into a run without leaking them to logs

## Software & Patching
- `software:view` - installed-software inventory
- `software:manage` - install / update / remove / os & third-party patches / rollback

## Policy Engine
- `policy:view` - read policies and drift
- `policy:manage` - edit, assign to groups or tag selectors, trigger reconcile

## Dashboards & Alerts
- `dashboard:view` - view dashboards
- `dashboard:manage` - create / share dashboards
- `alert:manage` - rule create/edit (targeting a group or tag selector) + ack

## Enrolment
- `enrolment:manage` - create/revoke tokens, view failures

## Identity & RBAC
- `identity:user:manage` - create / edit / disable users, api keys
- `identity:rbac:manage` - roles and direct permission grants
- `identity:sso:manage` - sso + mfa policy

## Audit
- `audit:view` - logs, config history
- `audit:export` - csv / json

## Integrations
- `integration:manage` - outbound webhook configuration

## Agent & Platform
- `agent:manage` - autoupdate config, canary rollout, rollback
- `platform:manage` - platform-level settings

## Implementation Detail
At the database layer, `Permission` entities define these exact strings. They are aggregated into `Role` entities using the `role_permissions` mapping table. For the SQL seed data establishing these along with default roles (Admin, Senior Technician, Junior Technician, Auditor), refer to `src/main/resources/db/migration/V004__rbac_seed.sql`.