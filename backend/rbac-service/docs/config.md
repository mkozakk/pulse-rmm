# Configuration & Startup

Application bootstrap, security context, and error handling.

### code
[`RbacServiceApplication.java`](../src/main/java/dev/pulsermm/rbac/RbacServiceApplication.java):
	- `main` - Entry point. Bootstraps Spring Application context, loads configuration from `application.yaml` and environment variables.

[`SecurityConfig.java`](../src/main/java/dev/pulsermm/rbac/config/SecurityConfig.java):
	- `securityFilterChain` - Defines route security: `/actuator/health` is public; `/api/organizations` and `/api/identity/users` require JWT (via `IdentityJwtAuthFilter`).
	- Protected endpoints checked against Keycloak's public key certificate from OIDC discovery endpoint.

[`OpenApiConfig.java`](../src/main/java/dev/pulsermm/rbac/config/OpenApiConfig.java):
	- `customOpenAPI` - Generates OpenAPI/Swagger schema from `@RestController` annotations.

[`GlobalExceptionHandler.java`](../src/main/java/dev/pulsermm/rbac/api/GlobalExceptionHandler.java):
	- Catches domain exceptions (`ConflictException`, `NotFoundException`, `ForbiddenException`) and returns RFC 7807 `ProblemDetail` responses.
	- Maps to HTTP status codes: 404, 409, 403, 400.

[`KeycloakAdminClient.java`](../src/main/java/dev/pulsermm/rbac/infrastructure/keycloak/KeycloakAdminClient.java):
	- Configured via environment variables: `${keycloak.url}`, `${keycloak.admin.username}`, `${keycloak.admin.password}`.
	- Auto-acquires and caches Bearer token for admin API calls.

### description
RBAC Service is a Spring Boot 3.3+ microservice running on `localhost:8082` (dev) or configured port (prod). On startup, it loads configuration from `application.yaml` and environment variables:
- `keycloak.url` - Keycloak instance URL (e.g., `http://keycloak:8080`)
- `keycloak.admin.username` / `keycloak.admin.password` - Credentials for Keycloak admin API
- `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password` - PostgreSQL connection
- `spring.redis.url` - Redis cache for permission resolution

`IdentityJwtAuthFilter` validates Bearer tokens by fetching Keycloak's public key certificate from the OIDC discovery endpoint (`/.well-known/openid-configuration`), enabling local validation without synchronous Keycloak calls on every request.

`GlobalExceptionHandler` ensures all REST errors return RFC 7807 `ProblemDetail` format (e.g., 409 for duplicate org name, 404 for missing org).
