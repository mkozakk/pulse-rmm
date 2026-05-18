# Endpoint Service (`backend/endpoint-service/`)

The Endpoint Service owns the complete lifecycle of managed endpoints in the fleet. It handles agent enrollment, cryptographic key validation, group and tag organization, agent version management and distribution, and remote session lifecycle management.

## Directory Structure

```text
endpoint-service/
├── docs/                   # Full documentation of endpoint-service features
├── src/main/java/.../
│   ├── endpoint/           # Endpoint aggregate root configuration
│   ├── enrolment/          # Agent enrollment, groups, tags, tokens
│   │   ├── api/            # REST controllers for enrollment and group management
│   │   ├── application/    # Enrollment, group, tag, and token services
│   │   ├── domain/         # Endpoint, Group, EnrolmentToken, EndpointTag entities
│   │   └── infrastructure/ # JPA repositories
│   ├── agentupdate/        # Agent binary versioning and distribution
│   │   ├── api/            # REST controller for version management
│   │   ├── application/    # Agent version service
│   │   ├── domain/         # AgentVersion entity
│   │   └── infrastructure/ # MinIO storage, repositories
│   └── remote/             # Remote session lifecycle
│       ├── api/            # REST controller for session management
│       ├── application/    # Session orchestration
│       ├── domain/         # DesktopSession entity
│       └── infrastructure/ # Repositories
├── src/main/resources/
│   └── db/migration/       # Flyway migrations (enrolment, agent_update, remote)
├── Dockerfile              # Containerization definition
└── pom.xml                 # Maven dependencies
```

## Features & Internal Documentation

* **[Enrollment & Groups](docs/enrollment.md)** - Enrollment flow, group hierarchy, invitation tokens, endpoint identity and key management.
* **[Tags & Auto-tagging](docs/tags.md)** - Tag lifecycle, tag rules for auto-tagging endpoints based on attributes, and tag-based filtering.
* **[Agent Updates](docs/agent-updates.md)** - Agent version management, binary distribution via MinIO, and versioning strategy.
* **[Remote Sessions](docs/remote-sessions.md)** - Remote session lifecycle, WebRTC signaling coordination, and session cleanup.

## Key Responsibilities

- **Enrollment** — Agents generate keypairs locally, present public key + token to the service, receive stable endpoint UUID and group assignment
- **Group Management** — Hierarchical group organization for permission scoping; endpoints belong to exactly one group
- **Tag System** — Free-form key=value labels for filtering, alerting, and policy targeting; supports auto-tagging via rules
- **Agent Updates** — Version lifecycle, artifact storage in MinIO, and distribution to agents on request
- **Remote Sessions** — Coordinates WebRTC signaling between browser and agent; manages session lifecycle and cleanup
