# API & Configuration (`api/`, `infrastructure/`, `/`)

Manages application entry points, local security perimeters, OpenAPI documentation schemas, and standardized exception handling.

### code
**Core Application**
[`ScriptServiceApplication.java`](../src/main/java/dev/pulsermm/script/ScriptServiceApplication.java):
	- The primary execution hook that bootstraps the embedded Spring Boot web server and activates the dependency injection context.

**Security & Documentation**
[`SecurityConfig.java`](../src/main/java/dev/pulsermm/script/infrastructure/config/SecurityConfig.java) & [`JwtAuthFilter.java`](../src/main/java/dev/pulsermm/script/api/JwtAuthFilter.java):
	- Establish a localized, zero-trust security context. Even though the API Gateway handles broad perimeter authentication, these components guarantee that any HTTP traffic hitting the Script Service internally (excluding private webhook endpoints) provides a valid JSON Web Token.
[`OpenApiConfig.java`](../src/main/java/dev/pulsermm/script/infrastructure/config/OpenApiConfig.java):
	- Automates the generation of Swagger schema definitions, providing strict contracts detailing the JSON shapes expected when creating scripts or triggering executions.

**Error Handling**
[`GlobalExceptionHandler.java`](../src/main/java/dev/pulsermm/script/api/errors/GlobalExceptionHandler.java):
	- Acts as a global safety net, intercepting both expected domain exceptions (like requesting a script that does not exist) and unexpected crashes. It formats these errors into safe, standardized problem detail payloads, preventing stack traces from leaking to the frontend.

### description
Every Spring Boot microservice requires foundational wiring to function safely within a distributed network. The `ScriptServiceApplication` serves as the core bootstrap mechanism, launching the application. Because this architecture adheres to zero-trust principles, the internal network is not considered inherently safe. The `SecurityConfig` and `JwtAuthFilter` are implemented locally to intercept inbound HTTP traffic requesting script modifications or executions. This ensures that even if an attacker successfully pivots laterally within the internal network, they cannot dispatch arbitrary code to the endpoint fleet without possessing a cryptographically signed JWT.

Furthermore, manipulating scripts and evaluating cryptographic routines can trigger numerous domain-specific errors (e.g., `SecretDecryptionException` or `ScriptRunNotFoundException`). To ensure predictable behavior for the frontend web application, the service implements a `GlobalExceptionHandler`. When these errors bubble up from the application layer, the handler catches them at the boundary. It logs the severity internally and returns a sanitized, standardized HTTP error response to the client, preserving system integrity and preventing sensitive cryptographic or database details from being exposed in a raw Java stack trace.
