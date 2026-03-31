# Identity Service (`backend/identity-service/`)

The Identity Service acts as the centralized authority for authentication and authorization. It manages users, roles, permissions, and issues the JSON Web Tokens (JWTs) utilized by the API Gateway to secure the infrastructure.

## Directory Structure
```text
identity-service/
├── docs/                   # Detailed feature documentation
├── src/main/java/.../
│   ├── api/                # REST Controllers for auth and RBAC, Exception Handlers
│   ├── application/        # Business logic for JWT generation, user management, and roles
│   ├── domain/             # Entities representing Users, Roles, Permissions, Refresh Tokens
│   └── infrastructure/     # Spring Data JPA repositories
├── Dockerfile              # Containerization definition
└── pom.xml                 # Maven dependencies
```

## Features & Internal Documentation

* **[Authentication & JWT](docs/auth.md)** - Explains user registration, login flows, token generation, and domain exceptions.
* **[Role-Based Access Control (RBAC)](docs/rbac.md)** - Details the comprehensive entity graph of users, roles, permissions, and endpoint groups.
* **[Permissions Catalog](docs/permissions.md)** - The complete catalog of granular permissions and what they grant.
* **[Core Configuration](docs/config.md)** - Covers security contexts, OpenAPI definitions, and global error handling logic.
