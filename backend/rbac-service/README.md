# RBAC Service (`backend/rbac-service/`)

The RBAC Service is the centralized authority for authentication, authorization, and identity management. It manages users, roles, permissions, organizations, and issues the JSON Web Tokens (JWTs) utilized by the API Gateway to secure the infrastructure.

## Directory Structure
```text
rbac-service/
├── docs/                   # Detailed feature documentation
├── src/main/java/dev/pulsermm/rbac/
│   ├── api/                # REST Controllers for auth, RBAC, users, organizations
│   ├── application/        # Business logic for JWT generation, user management, roles, permissions, organizations
│   ├── domain/             # Entities: Users, Roles, Permissions, Refresh Tokens, Organizations
│   └── infrastructure/     # Spring Data JPA repositories, OAuth2 clients
├── src/main/resources/
│   └── db/migration/       # Flyway migrations (users, roles, permissions, organizations)
├── Dockerfile              # Containerization definition
└── pom.xml                 # Maven dependencies
```

## Features & Internal Documentation

* **[Authentication & JWT](docs/auth.md)** - Explains user registration, login flows, token generation, refresh token rotation, logout, and domain exceptions.
* **[Role-Based Access Control (RBAC)](docs/rbac.md)** - Details the comprehensive entity graph of users, roles, permissions, endpoint groups, and direct permission grants.
* **[Organizations & Multi-Tenancy](docs/organizations.md)** - Covers organization isolation, multi-tenant deployments, and organization-scoped permissions.
* **[Permissions Catalog](docs/permissions.md)** - The complete catalog of granular permissions and what they grant.
* **[Core Configuration](docs/config.md)** - Covers security contexts, Keycloak OIDC integration, OpenAPI definitions, and global error handling logic.
