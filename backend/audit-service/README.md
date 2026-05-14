# Audit Service (`backend/audit-service/`)

Collects and persists an immutable record of all user-initiated actions across the Pulse RMM platform. Other services publish events to a RabbitMQ fanout exchange; the audit service consumes them, stores them in PostgreSQL, and exposes a filtered query and streaming export API.

## Directory Structure
```text
audit-service/
├── docs/                        # Full documentation of audit-service features
├── src/main/java/.../
│   ├── api/                     # REST controllers, DTOs, error handlers, JWT filter
│   ├── application/             # Query and export services
│   ├── domain/                  # AuditEvent JPA entity
│   └── infrastructure/
│       ├── config/              # RabbitMQ queue/exchange/binding declarations
│       ├── messaging/           # AMQP consumer that persists incoming events
│       └── persistence/         # Spring Data JPA repository with filter query
├── src/main/resources/
│   ├── db/migration/            # Flyway migrations (schema: audit)
│   └── application.yaml
├── Dockerfile
└── pom.xml
```

## Features & Internal Documentation

* **[Messaging & Persistence](docs/messaging.md)** - Explains how audit events flow from publisher services through RabbitMQ into the database.
* **[Query & Export API](docs/api.md)** - Covers the REST endpoints for browsing and downloading the audit log.
