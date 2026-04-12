# Agent Updates

## Overview

The Agent Update subsystem manages the lifecycle of agent binaries: versioning, artifact storage, and distribution to endpoints. The service acts as a distribution server; agents poll periodically for newer versions.

## Version Lifecycle

```
1. Admin publishes new version via POST /api/agent-updates/releases

2. Service stores version metadata (semver, release notes, artifact type)
   └─ Artifacts (exe, deb, rpm) uploaded separately to MinIO

3. Admin marks version as "current" (released)
   └─ Subsequent agent polls return this version

4. Agent fetches version metadata and signed download URLs

5. Agent downloads from MinIO using signed URL (time-limited)

6. Agent verifies signature (if enabled) and installs

7. Over time, admin deprecates old versions
   └─ Artifacts remain in storage; metadata can be marked deprecated
```

## Database Schema

```sql
agent_versions(
  id uuid pk,
  version varchar unique,         -- semver: 1.0.0, 1.0.1, etc.
  release_notes text,
  artifact_type varchar,          -- exe, msi, deb, rpm, pkg
  created_at timestamptz,
  published_at timestamptz,       -- null until admin releases
  deprecated_at timestamptz,      -- null if still in use
  created_by uuid fk (users)
)

-- tracks which artifacts exist for a version
agent_version_artifacts(
  id uuid pk,
  version_id uuid fk (agent_versions),
  platform varchar,               -- windows, linux_deb, linux_rpm
  filename varchar,
  minio_object_key varchar,       -- s3://bucket/agent/v1.0.0/agent-1.0.0.exe
  file_size_bytes bigint,
  sha256_hash varchar,            -- for integrity verification
  created_at timestamptz,
  uploaded_by uuid fk (users)
)
```

## Agent Update Checks

### Polling Frequency

- **On startup** - Agent checks for updates once
- **Daily** - Scheduled check every 24 hours
- **On demand** - Technician can trigger via API (future)

### Version Comparison

Agent compares locally installed version with latest published version:

```
Local:  1.0.2
Latest: 1.0.5
  → Update available, download and install

Local:  1.0.5
Latest: 1.0.5
  → No update needed

Local:  2.0.0-beta
Latest: 1.0.5
  → No update (beta > production; no downgrade)
```

## Artifact Storage

All binaries are stored in MinIO (S3-compatible):

```
Bucket: pulse-artifacts
  /agent/
    /v1.0.0/
      agent-1.0.0.exe          (Windows)
      agent-1.0.0.deb          (Debian/Ubuntu)
      agent-1.0.0.rpm          (Fedora/RHEL)
    /v1.0.1/
      agent-1.0.1.exe
      ...
```

**Signed download URLs:**
- Service generates time-limited signed URLs for agents
- URLs include query params: `X-Amz-Algorithm`, `X-Amz-Signature`, `X-Amz-Expires`
- Expiry: 1 hour (configurable)
- Agent downloads directly from MinIO without going through the API gateway

## API Endpoints

```
POST   /api/agent-updates/releases           Publish new version
GET    /api/agent-updates/releases           List all versions
GET    /api/agent-updates/releases/{id}      Get version details
PATCH  /api/agent-updates/releases/{id}      Mark deprecated
DELETE /api/agent-updates/releases/{id}      Delete version metadata (artifacts remain)

POST   /api/agent-updates/releases/{id}/upload-artifact  Upload binary
GET    /api/agent-updates/latest             Agent polls for latest version

(gRPC)
rpc GetLatestAgent(Empty) returns (AgentVersion);
  └─ Returns version, download URL, and SHA-256 for verification
```

## Error Handling

- `400 Bad Request` - Invalid version format, missing artifact type
- `404 Not Found` - Version not found
- `409 Conflict` - Version already published, artifact already exists
- `422 Unprocessable Entity` - Invalid semver
- `500 Internal Server Error` - MinIO communication error

## Rollback Strategy

No automatic rollback. If a bad version is published:

1. Admin creates new patch version (1.0.6 instead of 1.0.5)
2. Agent rollout proceeds (agents update to 1.0.6)
3. Old version (1.0.5) can be marked deprecated

**Downgrade prevention:** Agent will not downgrade to an older version (e.g., if latest is 1.0.5 and agent has 1.0.6, no update).
