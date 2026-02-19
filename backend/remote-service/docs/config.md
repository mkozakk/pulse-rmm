# API & Configuration (`api/`, `infrastructure/`, `/`)

Manages application entry points, local security perimeters, and global exception handling for the interactive session broker.

### code
**Core Application**
[`RemoteServiceApplication.java`](../src/main/java/dev/pulsermm/remote/RemoteServiceApplication.java):
	- The primary execution hook that bootstraps the embedded Spring Boot web server and activates the dependency injection context.

**Security Boundaries**
[`SecurityConfig.java`](../src/main/java/dev/pulsermm/remote/infrastructure/config/SecurityConfig.java) & [`JwtAuthFilter.java`](../src/main/java/dev/pulsermm/remote/api/JwtAuthFilter.java):
	- Establish a localized, zero-trust security context. Even though the API Gateway handles broad perimeter authentication, these components guarantee that any HTTP traffic hitting the Remote Service from the frontend (such as requesting to initiate a new desktop session) provides a valid, signed JSON Web Token.

**Error Handling**
[`GlobalExceptionHandler.java`](../src/main/java/dev/pulsermm/remote/api/errors/GlobalExceptionHandler.java):
	- Acts as a global safety net, intercepting expected domain exceptions (like `SessionNotFoundException` when a token expires) and unexpected crashes, formatting them into safe, standardized problem detail payloads rather than leaking raw Java stack traces to the client.

### description
Every Spring Boot microservice requires foundational wiring to function safely within a distributed network. The `RemoteServiceApplication` serves as the core bootstrap mechanism, launching the application and wiring its internal repositories and HTTP clients. Because this architecture adheres to zero-trust principles, the internal network is not considered inherently safe. The `SecurityConfig` and `JwtAuthFilter` are implemented locally to intercept inbound HTTP traffic requesting the creation of new interactive sessions. This ensures that even if an attacker successfully pivots laterally within the internal network, they cannot secretly generate session tokens and hijack remote endpoints without possessing a cryptographically signed JWT.

Furthermore, managing short-lived, stateful session tokens can trigger numerous domain-specific errors, particularly if a user attempts to use a token after its 30-second validity window has expired. To ensure predictable behavior for the frontend web application and the API Gateway, the service implements a `GlobalExceptionHandler`. When these errors bubble up from the application layer, the handler catches them at the boundary, returning a sanitized, standardized HTTP error response to the client. This preserves system integrity and prevents internal database query structures from being exposed in a raw Java stack trace.
