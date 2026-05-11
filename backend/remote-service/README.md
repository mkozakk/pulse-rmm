# Remote Service (`backend/remote-service/`)

The Remote Service handles the lifecycle and access control for interactive sessions, specifically WebRTC-based remote desktop control and interactive terminal shells. It issues the temporary session tokens required by the API Gateway's WebSocket proxies to secure real-time streams.

## Directory Structure
```text
remote-service/
├── docs/                   # Detailed feature documentation
├── src/main/java/.../
│   ├── api/                # REST Controllers, DTOs, Security Filters, and Exception Handling
│   ├── application/        # Business logic for session token generation and validation
│   ├── domain/             # Entities representing active Remote Sessions
│   └── infrastructure/     # Spring Data JPA repositories and internal clients (Gateway, Identity)
├── Dockerfile              # Containerization definition
└── pom.xml                 # Maven dependencies
```

## Features & Internal Documentation

* **[Session Lifecycle](docs/sessions.md)** - Details how interactive remote control sessions are securely requested, authorized, and tracked.
* **[API & Configuration](docs/config.md)** - Covers application bootstrapping, local security contexts, and global error handling.
