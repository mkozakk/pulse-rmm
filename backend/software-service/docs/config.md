# API & Configuration (`api/`, `/`)

Manages application entry points, local security perimeters, and OpenAPI documentation schemas.

### code
**Core Application**
[`SoftwareApplication.java`](../src/main/java/dev/pulsermm/software/SoftwareApplication.java):
	- The primary execution hook that bootstraps the embedded Spring Boot web server and activates the dependency injection context.

**Security & Documentation**
[`SecurityConfig.java`](../src/main/java/dev/pulsermm/software/api/SecurityConfig.java) & [`JwtAuthFilter.java`](../src/main/java/dev/pulsermm/software/api/JwtAuthFilter.java):
	- Establish a localized, zero-trust security context. Even though the API Gateway handles broad perimeter authentication, these components guarantee that any HTTP traffic hitting the Software Service from the frontend (such as requesting an inventory list) provides a valid JSON Web Token.
[`OpenApiConfig.java`](../src/main/java/dev/pulsermm/software/api/OpenApiConfig.java):
	- Automates the generation of Swagger schema definitions, providing strict contracts detailing the JSON shapes expected when querying software or dispatching installation commands.

### description
Every Spring Boot microservice requires foundational wiring to function safely within a distributed network. The `SoftwareApplication` serves as the core bootstrap mechanism, launching the application and wiring its internal repositories and controllers. Because this architecture adheres to zero-trust principles, the internal network is not considered inherently safe. The `SecurityConfig` and `JwtAuthFilter` are implemented locally to intercept inbound HTTP traffic requesting software modifications or inventory reads. This ensures that even if an attacker successfully pivots laterally within the internal network, they cannot view the software installed on the fleet or deploy malicious packages without possessing a cryptographically signed JWT. Additionally, to ensure seamless integration with the web application developers, the `OpenApiConfig` exposes a live documentation endpoint that maps out the exact structure of objects like `InstallRequest` and `SoftwareItemResponse`.
