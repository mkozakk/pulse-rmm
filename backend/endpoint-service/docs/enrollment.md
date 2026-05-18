# Enrollment & Groups

## Overview

The enrollment process establishes the initial trust relationship between an agent and the backend. The agent generates an ed25519 keypair locally, then presents the public key and an invitation token to establish its identity. Once enrolled, the endpoint is assigned a stable UUID and placed into a group.

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

1. **Created** — Admin calls POST /api/enrolment/tokens {group_id}
2. **Unused** — Token is valid and can be used to enroll an agent
3. **Used** — Agent successfully enrolled with token; token is marked used_at
4. **Expired** — Enrollment window closed (configurable TTL, default 24 hours)
5. **Revoked** — Admin manually revoked the token (admin-only action)

Tokens are one-time use. Attempting to use an already-used token returns the existing endpoint_id (idempotent).

## API Endpoints

```
POST   /api/enrolment/tokens              Create invitation token
GET    /api/enrolment/tokens              List tokens (admin only)
DELETE /api/enrolment/tokens/{id}         Revoke token (admin only)

GET    /api/groups                        List groups
POST   /api/groups                        Create group
GET    /api/groups/{id}                   Get group details
PATCH  /api/groups/{id}                   Update group (rename, move parent)
DELETE /api/groups/{id}                   Delete group (only if no endpoints)

GET    /api/endpoints                     List all endpoints
GET    /api/endpoints/{id}                Get endpoint details
PATCH  /api/endpoints/{id}                Update endpoint (move group, metadata)
```

## Error Handling

- `400 Bad Request` — Invalid input (missing group_id, invalid token format)
- `401 Unauthorized` — Token missing, expired, revoked, or already used
- `403 Forbidden` — User lacks permission to create tokens in the group
- `404 Not Found` — Group or endpoint does not exist
- `409 Conflict` — Group has children (cannot delete)
- `500 Internal Server Error` — Database error
