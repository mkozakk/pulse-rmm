# Script Service (`backend/script-service/`)

The Script Service acts as a secure, centralized library for automation scripts (PowerShell, Bash, Python, etc.). It manages the creation and approval of automation payloads, the secure storage of cryptographic secrets, and tracks the execution history of scripts dispatched to the endpoint fleet.

## Directory Structure
```text
script-service/
├── docs/                   # Detailed feature documentation
├── src/main/java/.../
│   ├── api/                # REST Controllers, DTOs, Security Filters, and Exception Handling
│   ├── application/        # Business logic for script execution, secret injection, and approval
│   ├── domain/             # Entities representing Scripts, Runs, Results, and Encrypted Secrets
│   └── infrastructure/     # Spring Data JPA repositories, Gateway clients, and AES encryptors
├── Dockerfile              # Containerization definition
└── pom.xml                 # Maven dependencies
```

## Features & Internal Documentation

* **[Script Library](docs/library.md)** - Explains how automation payloads are defined, stored, and managed via the API.
* **[Execution & Routing](docs/execution.md)** - Details how scripts are queued, dispatched to agents via the Gateway, and how output is tracked.
* **[Secret Management](docs/secrets.md)** - Covers the cryptographic mechanisms used to secure sensitive variables at rest.
* **[API & Configuration](docs/config.md)** - Covers application bootstrapping, local security contexts, and global error handling.
