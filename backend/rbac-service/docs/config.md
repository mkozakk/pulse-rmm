# Configuration & Core (`api/`, `config/`, `/`)

Manages application entry points, global error handling, and security contexts.

### code
[`IdentityServiceApplication.java`](../src/main/java/dev/pulsermm/identity/IdentityServiceApplication.java):
	- `main` - The primary entry point that bootstraps the Spring Application context, initializes dependencies, and binds the microservice to its designated network port.

[`SecurityConfig.java`](../src/main/java/dev/pulsermm/identity/config/SecurityConfig.java):
	- `securityFilterChain` - Configures the Spring Security context, explicitly defining which routes are public (like login, register, and refresh) and which require the localized `IdentityJwtAuthFilter` to be intercepted.

[`OpenApiConfig.java`](../src/main/java/dev/pulsermm/identity/config/OpenApiConfig.java):
	- `customOpenAPI` - Generates the Swagger/OpenAPI documentation definitions, allowing frontend developers and API clients to understand the exact JSON schemas expected by the Identity Service.

[`GlobalExceptionHandler.java`](../src/main/java/dev/pulsermm/identity/api/GlobalExceptionHandler.java):
	- `handleException` - Intercepts all unhandled exceptions or specific domain errors thrown by the application layer (e.g., `UsernameTakenException`) and prevents stack traces from leaking to the client.

[`ErrorResponse.java`](../src/main/java/dev/pulsermm/identity/api/dto/ErrorResponse.java):
	- `ErrorResponse` - A standardized Data Transfer Object used exclusively by the exception handler to format all failed requests into a consistent JSON structure compliant with RFC 7807 problem details.

### description
Every Spring Boot microservice requires foundational wiring to function predictably within the broader ecosystem. The `IdentityServiceApplication` serves as the ignition switch, booting the embedded web server. Because the API Gateway handles perimeter security, internal microservices often run in a trusted network segment. However, the Identity Service requires its own localized `SecurityConfig` and `IdentityJwtAuthFilter` because it exposes its own administrative endpoints (like creating roles) that must be secured independently of the Gateway's internal permission checks. To ensure system stability and predictable frontend interactions, the service implements a `GlobalExceptionHandler`. Instead of allowing the application to crash or leak raw Java stack traces back to the browser when an error occurs (such as a database constraint violation), this handler catches the exception globally. It logs the error internally, then maps it to an appropriate HTTP status code (like 400 Bad Request or 409 Conflict), wrapping the user-friendly message inside an `ErrorResponse` payload.
