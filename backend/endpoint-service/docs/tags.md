# Tags & Auto-tagging

## Overview

Tags are free-form key=value labels attached to endpoints. They serve three purposes:

1. **Ad-hoc filtering** - Technician selects endpoints by tag (e.g., `env=prod`, `site=warsaw`)
2. **Alert targeting** - Alert rules apply to endpoints matching tag selectors
3. **Policy targeting** - Policies are applied to groups of endpoints selected by tags

Unlike groups (which are hierarchical and exclusive), tags are:
- Many-to-many (an endpoint can have any number of tags)
- Flat (no hierarchy)
- Mutable (can be added/removed at runtime)

## Tag Sources

Tags come from three sources:

### 1. Enrollment Parameters

Admin includes tags when creating the enrollment token:

```
POST /api/enrolment/tokens {
  group_id: "...",
  tags: {
    env: "prod",
    site: "warsaw",
    role: "dev-laptop"
  }
}
```

All endpoints enrolled with this token receive these initial tags.

### 2. Auto-tagging Rules

Rules automatically apply tags based on endpoint attributes:

```
POST /api/tag-rules {
  name: "prod-servers-by-hostname",
  selector: {
    hostname_pattern: "^prod-.*",
    os: "linux"
  },
  tags: {
    env: "prod",
    tier: "backend"
  }
}
```

**Available attributes for matching:**
- `hostname_pattern` - Regex match on hostname
- `os` - Exact match (windows, linux)
- `arch` - Exact match (x86_64, arm64)
- `ip_range` - CIDR block (future)

**Evaluation:** Rules are evaluated:
- On enrollment (during gRPC Enrol)
- On periodic reconciliation job (every 1 hour)
- Manually via PATCH /api/endpoints/{id}/reconcile-tags

### 3. Manual Assignment

Technician adds/removes tags via REST API:

```
PATCH /api/endpoints/{id}/tags
{
  added: { cluster: "east-1" },
  removed: ["old-tag"]
}
```

## Database Schema

```sql
-- tags: the actual key=value pairs
endpoint_tags(
  id uuid pk,
  endpoint_id uuid fk (endpoints),
  key varchar,
  value varchar,
  source varchar,              -- enrolled, rule, manual
  created_at timestamptz,
  created_by uuid fk (users),  -- null for automated rules
  unique(endpoint_id, key)
)

-- tag_rules: auto-tagging rules
tag_rules(
  id uuid pk,
  name varchar unique,
  selector_hostname_pattern varchar,
  selector_os varchar,
  selector_arch varchar,
  tags_json jsonb,             -- {key1: value1, key2: value2}
  active boolean,
  created_at timestamptz,
  created_by uuid fk (users)
)
```

## API Endpoints

```
GET    /api/endpoints/{id}/tags            List tags on endpoint
PATCH  /api/endpoints/{id}/tags            Add/remove tags
GET    /api/tag-rules                      List all rules
POST   /api/tag-rules                      Create rule
GET    /api/tag-rules/{id}                 Get rule details
PATCH  /api/tag-rules/{id}                 Update rule
DELETE /api/tag-rules/{id}                 Delete rule
PATCH  /api/endpoints/{id}/reconcile-tags  Force re-evaluation of auto-tagging rules
```

## Tag Query Syntax

Endpoints can be filtered by tags using selector syntax:

```
GET /api/endpoints?tags=env:prod&tags=site:warsaw
  → Returns endpoints with tag env=prod AND site=warsaw

GET /api/endpoints?tags=env:prod,staging
  → Returns endpoints with (env=prod OR env=staging)

GET /api/endpoints?tags=!cluster:west
  → Returns endpoints WITHOUT cluster=west
```

## Idempotency & Conflicts

**Adding a tag that already exists** - No error; treated as idempotent update.

**Tag key uniqueness** - An endpoint can have only one value per key. Example:

```
Initial: {env: prod}

PATCH {added: {env: staging}}
  → Existing env=prod is replaced with env=staging
  → Result: {env: staging}
```

**Source tracking** - When a tag is overwritten:
- Manual override always wins
- Auto-tagging rule updates only if no manual value

Example:

```
1. Rule sets env=staging (source: rule)
2. Manual PATCH {added: {env: prod}} (source: manual)
3. Rule re-evaluation runs, rule says env=staging
   → env remains prod (manual value is preserved)
```

## Error Handling

- `400 Bad Request` - Invalid key/value format, unsupported selector field
- `404 Not Found` - Endpoint or rule not found
- `409 Conflict` - Rule name not unique, selector is empty
- `422 Unprocessable Entity` - Regex pattern is invalid
