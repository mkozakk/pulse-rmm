# Software Service (`backend/software-service/`)

The Software Service acts as the central intelligence for application lifecycle management across the endpoint fleet. It tracks the inventory of installed applications on every agent and orchestrates the deployment and removal of software packages.

## Directory Structure
```text
software-service/
├── docs/                   # Detailed feature documentation
├── src/main/java/.../
│   ├── api/                # REST Controllers, DTOs, Security Filters, and Webhook Receivers
│   ├── application/        # Business logic for managing installations and inventory updates
│   ├── domain/             # Entities representing Installed Software and Installation Commands
│   └── infrastructure/     # Spring Data JPA repositories and internal Gateway clients
├── Dockerfile              # Containerization definition
└── pom.xml                 # Maven dependencies
```

## Features & Internal Documentation

* **[Inventory Tracking](docs/inventory.md)** - Explains how the service processes telemetry to maintain an accurate, searchable list of installed applications per endpoint.
* **[Package Management](docs/packages.md)** - Details how the service triggers software installations and removals asynchronously via the API Gateway.
* **[API & Configuration](docs/config.md)** - Covers application bootstrapping, local security contexts, and OpenAPI generation.
