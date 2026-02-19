# Configuration & Core (`api/`, `/`)

Manages application entry points, local perimeter security, OpenAPI generation, and global exception mapping.

### code
[`EnrolmentApplication.java`](../src/main/java/dev/pulsermm/enrolment/EnrolmentApplication.java):
	- `main` - The primary execution hook that bootstraps the embedded Spring Boot server and activates the dependency injection context.

[`SecurityConfig.java`](../src/main/java/dev/pulsermm/enrolment/api/security/SecurityConfig.java) & [`JwtAuthFilter.java`](../src/main/java/dev/pulsermm/enrolment/api/security/JwtAuthFilter.java):
	- `securityFilterChain` & `doFilterInternal` - Establish a local security context for the microservice. While the API Gateway handles broad perimeter security, this filter ensures that internal REST calls directly hitting the Enrolment Service still possess a valid JSON Web Token, maintaining zero-trust architecture principles.

[`OpenApiConfig.java`](../src/main/java/dev/pulsermm/enrolment/api/config/OpenApiConfig.java):
	- `customOpenAPI` - Automates the generation of Swagger schema definitions, mapping out the expected JSON request and response shapes for the entire service to assist frontend developers.

[`GlobalExceptionHandler.java`](../src/main/java/dev/pulsermm/enrolment/api/errors/GlobalExceptionHandler.java):
	- `handleException` - Acts as a global safety net, intercepting expected domain exceptions (like invalid tokens) or unexpected crashes, formatting them into safe, standardized error payloads rather than leaking stack traces.

### description
Every Spring Boot microservice requires foundational wiring to function safely within a distributed network. The `EnrolmentApplication` serves as the core bootstrap mechanism, launching the application and wiring its internal beans. Because this architecture adheres to zero-trust principles, trusting the API Gateway implicitly is insufficient. The `SecurityConfig` and `JwtAuthFilter` are implemented locally within the Enrolment Service to intercept all inbound HTTP traffic. This ensures that even if an attacker pivots laterally within the internal network, they cannot invoke internal group management endpoints without possessing a cryptographically signed JWT. To ensure stability during operations, the service implements a `GlobalExceptionHandler`. If an agent submits an invalid bootstrap payload, triggering a domain exception deep within the application layer, this handler catches the error at the boundary. It logs the infraction securely and returns a sanitized, standardized HTTP error response to the client, preserving system integrity and preventing sensitive infrastructure details from being exposed.
