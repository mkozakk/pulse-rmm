# Enrollment, Groups, & Installation

## Overview

The enrollment process establishes the initial trust relationship between an agent and the backend. The agent generates an ed25519 keypair locally, then presents the public key and an invitation token to establish its identity. Once enrolled, the endpoint is assigned a stable UUID and placed into a group. Installation scripts (Bash and PowerShell) are generated dynamically and configure the agent with enrollment credentials.

## Enrollment Flow

```
1. Admin creates an enrollment token via POST /api/enrolment/tokens {group_id}
   └─ Token is scoped to a specific group and has an expiry time

2. Agent is configured with PULSE_TOKEN=<token>

3. Agent generates ed25519 keypair locally (once, persisted to disk)

4. Agent calls gRPC Enrol {token, public_key, hostname, os, arch}

5. Enrolment Service validates token:
   - Check token exists and is not expired
   - Check token is not already consumed (idempotency check)
   - Verify token belongs to the group

6. Service creates endpoint record:
   - Generates UUID (unique endpoint ID)
   - Stores public_key (ed25519)
   - Records group_id from token
   - Sets initial status=online

7. Service returns endpoint_id to agent
   └─ Agent persists endpoint_id + private_key locally

8. Endpoint is now enrolled and appears in GET /api/endpoints
```

## Database Schema

```sql
-- groups: hierarchical organization
groups(
  id uuid pk,
  name varchar,
  parent_id uuid fk (groups),
  created_at timestamptz
)

-- enrollment_tokens: one-time invitation codes
enrolment_tokens(
  id uuid pk,
  group_id uuid fk (groups),
  token_hash varchar unique,    -- SHA-256 of actual token (never store plaintext)
  created_at timestamptz,
  expires_at timestamptz,
  used_at timestamptz,          -- not null if already consumed
  revoked_at timestamptz
)

-- endpoints: enrolled agents
endpoints(
  id uuid pk,                   -- stable endpoint identifier
  group_id uuid fk (groups),
  hostname varchar,
  os varchar,                   -- windows, linux, macos
  arch varchar,                 -- x86_64, arm64
  public_key bytea,             -- ed25519 public key (DER-encoded)
  status varchar,               -- online, offline
  enrolled_at timestamptz,
  last_seen timestamptz,
  created_at timestamptz
)
```

## Idempotency

Enrollment is idempotent by public key. If an agent enrolls twice with the same public key:

```
1. First enrollment: new endpoint created, token marked as used
2. Second enrollment with same token: service checks token is used, returns existing endpoint_id
3. Same endpoint_id returned both times
```

This ensures the agent can retry without creating duplicates.

## Group Hierarchy

Groups form a tree structure:

```
root (everyone)
├─ asia
│  ├─ singapore (laptops)
│  └─ tokyo (desktops)
└─ americas
   ├─ new-york
   └─ san-francisco
```

**Permission inheritance:** Granting `remote:desktop:control` on `/americas` implicitly grants it on all descendants (new-york, san-francisco).

**Endpoints belong to exactly one group.** Use tags for cross-cutting organization (e.g., `env=prod` applies to endpoints across multiple groups).

## Token Lifecycle

1. **Created** - Admin calls POST /api/enrolment/tokens {group_id}
2. **Unused** - Token is valid and can be used to enroll an agent
3. **Used** - Agent successfully enrolled with token; token is marked used_at
4. **Expired** - Enrollment window closed (configurable TTL, default 24 hours)
5. **Revoked** - Admin manually revoked the token (admin-only action)

Tokens are one-time use. Attempting to use an already-used token returns the existing endpoint_id (idempotent).

## Installation Scripts

Installation scripts (Bash for Linux, PowerShell for Windows) are dynamically generated and delivered via public endpoints. Scripts embed the enrollment token and API URL, configure the agent, install runtime dependencies, and start the service.

### Linux Installation (Bash)

```bash
curl -fsSL https://pulse-api.example.com/install/{tokenId}.sh | sudo bash
```

**Script functionality:**
- Validates root privileges
- Creates `/etc/pulse-agent/` with config (api_url, token, data_dir, log_level)
- Detects package manager (apt, dnf, rpm) and installs runtime dependencies:
  - ffmpeg (for screen capture)
  - xdg-desktop-portal + backends (GNOME, GTK, KDE - for Wayland/X11 remote desktop)
- Downloads agent binary (deb or rpm) from MinIO
- Verifies checksum via SHA-256
- Installs package and starts systemd service
- Optionally starts desktop helper for current user (if run via sudo)
- Warns if ffmpeg lacks pipewiregrab support (Wayland limitation)

**Dependencies handled by script:**
- Ubuntu/Debian: `apt-get install ffmpeg xdg-desktop-portal xdg-desktop-portal-gnome ...`
- Fedora/RHEL: `dnf install ffmpeg-free xdg-desktop-portal ...`
- Raw RPM: User must install dependencies manually (no package manager available)

### Windows Installation (PowerShell)

```powershell
Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -Command "iex (iwr -Uri ''https://pulse-api.example.com/install/{tokenId}.ps1'' -UseBasicParsing).Content"'
```

**Script functionality:**
- Requires administrator privileges (enforced via `#Requires -RunAsAdministrator`)
- Downloads exe from MinIO to `$env:TEMP`
- Verifies checksum via SHA-256
- Stops and removes existing `PulseAgent` service if present
- Copies binary to `$env:ProgramFiles\PulseAgent`
- Creates config at `$env:ProgramData\pulse-agent\config.yaml`
- Registers Windows service (startup type: Automatic)
- Starts service
- Displays status check and log viewing commands

### Template Variables

Both templates use simple string replacement:

```
{{API_URL}}    - Replaced with pulse.api.url config value
{{TOKEN}}      - Replaced with enrollment token UUID
```

### EndpointController Endpoints

```
GET    /install/{tokenId}.sh              Linux install script (text/x-shellscript)
       └─ Validates token exists and is not expired
       └─ Returns 404 if token missing/invalid/expired

GET    /install/{tokenId}.ps1             Windows install script (text/plain)
       └─ Validates token exists and is not expired
       └─ Returns 404 if token missing/invalid/expired
```

## Certificate Renewal

Endpoints can renew their TLS certificates by submitting a Certificate Signing Request (CSR). The service validates the endpoint exists and is not revoked, then forwards the CSR to the CA for signing. Certificates are required for agent ↔ gateway communication.

### InternalCertController Endpoints

These endpoints are **internal-only** (no JWT required; protected by `X-Internal-Token` header):

```
POST   /internal/cert/renew               Renew endpoint certificate
       Request: {endpointId, csrPem}
       Response: {certPem, caBundlePem}
       └─ 200 OK - Certificate issued
       └─ 400 Bad Request - Invalid CSR or missing field
       └─ 403 Forbidden - Endpoint revoked
       └─ 404 Not Found - Endpoint does not exist
       └─ 500 Internal Server Error - CA service unavailable

GET    /internal/endpoints/{id}/revoked   Check revocation status
       Response: {endpointId, revoked}
       └─ 200 OK - Status returned
       └─ 400 Bad Request - Invalid UUID format
```

**Revocation check flow:**
1. Agent submits CSR for renewal
2. Service calls GET `/internal/endpoints/{id}/revoked` to check status
3. If revoked, CSR is rejected (403 Forbidden)
4. Otherwise, CSR forwarded to CA for signing

### CertRenewService Logic

```java
public RenewResult renew(UUID endpointId, String csrPem) {
  // 1. CA service must be enabled (pulse.ca.enabled=true)
  // 2. CSR must not be empty
  // 3. Endpoint must exist in database
  // 4. Endpoint must not be in revocation list
  // 5. Forward CSR to CA service, return signed cert + bundle
}
```

**Exceptions:**
- `IllegalStateException` - CA service disabled
- `IllegalArgumentException` - CSR empty/invalid
- `UnknownEndpointException` - Endpoint not found
- `RevokedEndpointException` - Endpoint revoked

## Endpoint Movement

Endpoints can be moved between groups (within the same organization) via the MoveEndpointService. This updates the endpoint's group assignment and notifies the identity service for permission inheritance.

### MoveEndpointService Logic

```
1. Validate endpoint exists
2. Validate target group exists
3. Check source and target groups belong to same organization
4. Update endpoint.group_id
5. Call identity-service to update permission inheritance
```

**Constraints:**
- Cannot move endpoint across organizations (403 Forbidden equivalent)
- Both source and target groups must exist
- Cross-org moves are prevented (even if auth allows)

## Enrollment (Agent-to-Service)

Agents enroll by submitting their public key and a CSR. The InternalEnrolController handles agent enrollment calls and endpoint-to-organization routing.

### InternalEnrolController Endpoints

These endpoints are **internal-only** (no JWT required):

```
POST   /internal/enrol                    Agent enrollment
       Request: {token, publicKey, hostname, os, arch, csrPem}
       Response: {endpointId, certPem, caBundlePem}
       └─ 200 OK - Enrollment successful
       └─ 401 Unauthorized - Token missing/expired/invalid
       └─ 500 Internal Server Error - Enrollment failed

GET    /internal/endpoints/{id}/org       Resolve endpoint's organization
       Required header: X-Internal-Token
       Response: UUID (orgId)
       └─ 200 OK - orgId returned
       └─ 204 No Content - Endpoint has no org (global group) or unknown
       └─ 403 Forbidden - Invalid X-Internal-Token header

POST   /internal/heartbeat                Endpoint heartbeat
       Request: {endpointId}
       Response: 200 OK
       └─ Updates endpoint.last_seen to current time
       └─ No error if endpoint not found (idempotent)
```

**Enrollment flow:**
1. Agent calls POST `/internal/enrol` with token + public key + CSR
2. Service validates token (exists, not expired, not revoked)
3. Service generates endpoint UUID
4. Service forwards CSR to CA, receives signed certificate
5. Service saves endpoint with public key and group assignment
6. Service returns endpoint UUID + certificate to agent

**Heartbeat flow:**
1. Agent periodically calls POST `/internal/heartbeat` with endpoint UUID
2. Service updates `last_seen` timestamp
3. Used to detect agent offline status (>90s no heartbeat = offline)

## API Endpoints Summary

### Public Endpoints (JWT required)

```
POST   /api/enrolment/tokens              Create invitation token
       Request: {groupId, ttlHours}
       Response: {id, expiresAt, installSh, installPs1}
       └─ Returns both Bash and PowerShell one-liners for agent installation

GET    /api/endpoints                     List endpoints (tag filtering optional)
       Query params: ?tag=key=value (repeated for AND logic)
       Response: List<EndpointResponse>
       └─ Org-scoped callers see only their org's endpoints
       └─ Global admins see all endpoints

GET    /api/endpoints/{id}                Get endpoint details
       Response: EndpointResponse (hostname, os, arch, enrolledAt, lastSeen, tags)

POST   /api/endpoints/{id}/revoke         Revoke endpoint (deny future cert renewals)
       Request: {reason} (optional)
       └─ 204 No Content

PUT    /api/endpoints/{id}/tags           Update endpoint tags
       Request: {tags: {key: value, ...}}

PUT    /api/endpoints/{id}/group          Move endpoint to group
       Request: {groupId}
       └─ Validates same organization

POST   /api/groups                        Create group
       Request: {name, parentId}
       Response: GroupResponse

GET    /api/groups                        List groups
       Response: List<GroupResponse>
       └─ Org-scoped callers see only their org's groups

POST   /api/tag-rules                     Create auto-tagging rule
       Request: {conditionField, conditionValue, tagKey, tagValue}
       Response: TagRuleResponse

GET    /api/tag-rules                     List tag rules
       Response: List<TagRuleResponse>

DELETE /api/tag-rules/{id}                Delete tag rule

POST   /api/tag-rules/evaluate            Evaluate all rules against all endpoints
       └─ Manual trigger (rules also evaluate on enrollment + 1-hour reconciliation job)
```

### Internal Endpoints (no JWT, protected by X-Internal-Token or none)

```
GET    /install/{tokenId}.sh              Linux install script
GET    /install/{tokenId}.ps1             Windows install script

POST   /internal/enrol                    Agent enrollment (gRPC-like)
GET    /internal/endpoints/{id}/org       Organization lookup
POST   /internal/heartbeat                Endpoint heartbeat
POST   /internal/cert/renew               Certificate renewal
GET    /internal/endpoints/{id}/revoked   Revocation status
```

## Error Handling

**REST endpoints (public) return RFC 7807 ProblemDetail:**
- `400 Bad Request` - Invalid input (missing group_id, invalid token format)
- `401 Unauthorized` - Token missing, expired, revoked, or already used; missing JWT
- `403 Forbidden` - User lacks permission; endpoint revoked
- `404 Not Found` - Group, endpoint, or token does not exist
- `409 Conflict` - Group has children (cannot delete), cross-org move attempted
- `500 Internal Server Error` - Database/CA service error

**Internal endpoints return simple JSON error responses:**
- Errors do not require RFC 7807 format (internal-only endpoints)
- HTTP status codes still used (400, 401, 403, 404, 500)
