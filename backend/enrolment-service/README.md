# Enrolment Service (`backend/enrolment-service/`)

The Enrolment Service manages the lifecycle of physical endpoints. It handles the secure registration (enrolment) of new agents, assigns them unique identifiers, validates their cryptographic identities, and organizes them into logical groupings and dynamic tags.

## Directory Structure
```text
enrolment-service/
├── docs/                   # Detailed feature documentation
├── src/main/java/.../
│   ├── api/                # REST Controllers, DTOs, Security Filters, and Exception Handling
│   ├── application/        # Business logic orchestrating enrolment, grouping, and tagging
│   ├── domain/             # Entities representing Endpoints, Groups, Tags, and Tokens
│   └── infrastructure/     # Spring Data JPA repositories and internal gRPC/HTTP clients
├── Dockerfile              # Containerization definition
└── pom.xml                 # Maven dependencies
```

## Features & Internal Documentation

* **[Agent Enrolment & Identity](docs/enrolment.md)** - Details how tokens are generated, how agents bootstrap trust via gRPC, and how identities are bound.
* **[Organization & Tagging](docs/organization.md)** - Explains hierarchical groups, manual tagging, and dynamic tag rules.
* **[API & Configuration](docs/config.md)** - Covers the entry point, local security contexts, and global error handling.
