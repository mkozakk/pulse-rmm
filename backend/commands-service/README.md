# Commands Service (`backend/commands-service/`)

The Commands Service orchestrates command delivery to endpoints across three domains: script execution, software package management, and process discovery/control. It manages script payloads and execution tracking, discovers and manages endpoint software inventory, and enables administrators to list and kill processes remotely.

## Directory Structure
```text
commands-service/
├── docs/                   # Detailed feature documentation
├── src/main/java/dev/pulsermm/commands/
│   ├── api/                # REST Controllers, DTOs, Security Filters, and Exception Handling
│   ├── application/        # Business logic for script, software, and process orchestration
│   ├── domain/             # Entities: Scripts, SoftwareItems, ProcessSnapshots, Commands
│   ├── infrastructure/     # Spring Data JPA repositories, AgentHubClient, Repositories
│   ├── scripts/            # Script domain
│   ├── software/           # Software management domain
│   └── processes/          # Process discovery and control domain
├── Dockerfile              # Containerization definition
└── pom.xml                 # Maven dependencies
```

## Features & Internal Documentation

* **[Script Library](docs/library.md)** - Explains how automation payloads are defined, stored, and managed via the API.
* **[Execution & Routing](docs/execution.md)** - Details how scripts are queued, dispatched to agents via the Gateway, and how output is tracked.
* **[Software Management](docs/software.md)** - Covers endpoint software discovery, inventory management, and package operations (install, update, remove).
* **[Process Management](docs/processes.md)** - Describes process listing and termination capabilities for remote task management.
* **[Secret Management](docs/secrets.md)** - Covers the cryptographic mechanisms used to secure sensitive variables at rest.
* **[API & Configuration](docs/config.md)** - Covers application bootstrapping, local security contexts, and global error handling.
