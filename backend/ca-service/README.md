# CA Service (`backend/ca-service/`)

The Certificate Authority service issues, rotates, and revokes mTLS certificates for agent endpoints. Every agent connection to the backend is authenticated via mutual TLS, and this service manages the full certificate lifecycle.

## Directory Structure

```text
ca-service/
├── docs/                   # Detailed feature documentation
├── src/main/java/dev/pulsermm/ca/
│   ├── api/                # REST controller for certificate operations
│   ├── application/        # CaService with CSR signing, rotation, revocation logic
│   ├── config/             # Spring configuration, OpenAPI
│   └── infrastructure/
│       ├── CaRootRepository        # Stores root CA key and cert in database
│       └── CaKeyEncryptor          # Encrypts root key at rest
├── src/main/resources/
│   └── db/migration/       # Flyway migration: root CA initialization
├── Dockerfile              # Containerization definition
└── pom.xml                 # Maven dependencies
```

## Key Responsibilities

- **Root CA Initialization** — Generates and stores the encrypted root CA key and certificate on first startup
- **CSR Signing** — Accepts certificate signing requests (CSR) from agents and issues X.509 certificates
- **Certificate Rotation** — Agents can request certificate renewal before expiry (same CSR process)
- **Revocation List Management** — Maintains the revocation list of expired or compromised agent certificates
- **Validation** — Provides the root CA certificate and revocation list to agent-hub for connection validation

## Features & Internal Documentation

* **[Certificate Lifecycle](docs/certificate-lifecycle.md)** - Explains CSR generation, signing, rotation, and expiration; the certificate identity and validity period.
* **[Revocation](docs/revocation.md)** - Covers revocation list (CRL) management, how agent-hub validates certificates against the CRL, and revoking an endpoint.

## Networking

- **HTTP Server** — Listens on `localhost:8089` (configurable). Hosts REST endpoints for CSR signing and certificate renewal.
- **Database** — PostgreSQL schema `ca` stores root CA key (encrypted), issued certificates, and revocation list.
- **Encryption Key** — Root CA private key is encrypted at rest with AES-256; the encryption key is the environment variable `CA_ENCRYPTION_KEY`.

## Architecture Notes

**Minimal public surface.** The CA service is mostly internal:
- Agents call it for CSR signing and renewal (via the API Gateway or directly)
- Agent-hub calls it for the revocation list (on startup and periodically)
- No user-facing API; the CA cert/CRL are not user-facing

**Root CA on startup.** On first startup, if no root CA exists in the database, the service generates one:
- Generates a 2048-bit RSA key
- Self-signs it with a 10-year validity period (configurable)
- Encrypts the private key with `CA_ENCRYPTION_KEY`
- Stores root CA public cert and encrypted private key in the `ca_roots` table

**Per-agent certificates.** Each agent gets:
- A unique certificate issued by the root CA
- Subject CN = endpoint UUID (the agent's identity)
- Validity period: typically 1 year (configurable)
- Can be revoked individually

**No CRL delivery channel.** The revocation list is not published to a public HTTP endpoint. Instead, agent-hub queries the CA service on startup and periodically (every 1 hour) to fetch the latest CRL.

## Related Services

- **[Agent Hub](../agent-hub/README.md)** — Validates agent certificates against the revocation list on every connection
- **[Endpoint Service](../endpoint-service/README.md)** — Initiates agent enrollment, which includes CSR signing
- **[API Gateway](../api-gateway/README.md)** — Routes enrollment and certificate renewal requests to ca-service

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `CA_ENCRYPTION_KEY` | (required) | AES-256 encryption key for root CA private key (32 bytes, base64-encoded) |
| `CA_ROOT_CN` | `Pulse RMM CA` | Common name for the root CA certificate |
| `CA_ROOT_VALIDITY_DAYS` | `3650` | Root CA validity period in days (10 years) |
| `CA_AGENT_VALIDITY_DAYS` | `365` | Agent certificate validity period in days (1 year) |
| `POSTGRES_HOST` | `postgres` | PostgreSQL hostname |
| `POSTGRES_DB` | `pulse` | PostgreSQL database name |
| `POSTGRES_USER` | `pulse` | PostgreSQL username |
| `POSTGRES_PASSWORD` | `pulse` | PostgreSQL password |

## Security Considerations

**Encrypted Root Key.** The root CA private key is encrypted at rest with AES-256. The encryption key must be stored securely (e.g., in a secrets manager, not in a config file).

**Key Rotation.** Rotating the root CA key is not currently supported. To rotate:
1. Generate a new root CA with a new encryption key
2. Migrate all agents to the new CA
3. This is a production deployment task, not a feature

**Certificate Revocation.** Revoking an agent certificate:
1. Immediately prevents new connections (agent-hub checks CRL on every connect)
2. Does not kill existing connections (done via stream termination in agent-hub)
3. Is irreversible (revoked certs cannot be un-revoked)

**Compromise Recovery.** If the root CA key is compromised:
1. All agent certificates are compromised
2. Revoke all agents immediately
3. Rotate the root CA key (requires downtime)
4. Re-enroll all agents with new certificates
