# API & Configuration (`api/`, `/`)

Manages application entry points, internal security boundaries, OpenAPI documentation schemas, and the REST interfaces used to retrieve aggregated telemetry.

### code
**Core Application**
[`MetricApplication.java`](../src/main/java/dev/pulsermm/metric/MetricApplication.java):
	- The primary execution hook that bootstraps the embedded Spring Boot web server, activates dependency injection, and initializes the TimescaleDB connection pools.

**API & Routing**
[`MetricController.java`](../src/main/java/dev/pulsermm/metric/api/MetricController.java):
	- Exposes standard REST HTTP endpoints allowing the frontend web application to query aggregated, historical hardware statistics and current operational statuses for rendering charts and dashboards.

**Security & Documentation**
[`SecurityConfig.java`](../src/main/java/dev/pulsermm/metric/api/SecurityConfig.java) & [`JwtAuthFilter.java`](../src/main/java/dev/pulsermm/metric/api/JwtAuthFilter.java):
	- Establish a localized, zero-trust security context. Even though the API Gateway handles broad perimeter authentication, these components ensure that any internal HTTP traffic attempting to pull sensitive telemetry data from the `MetricController` still provides a valid JSON Web Token.
[`OpenApiConfig.java`](../src/main/java/dev/pulsermm/metric/api/OpenApiConfig.java):
	- Automates the generation of Swagger schema definitions, providing strict contracts detailing the JSON shapes expected when querying historical metrics, assisting in frontend integration.

### description
Every Spring Boot microservice requires foundational wiring to function predictably within a distributed environment. The `MetricApplication` serves as the ignition switch, booting the server and wiring the internal components necessary to interface with TimescaleDB. While the ingestion pipeline operates heavily on gRPC, the service must also provide a mechanism for administrators to actually view the data. The `MetricController` solves this by exposing standard REST endpoints that the web application can query to generate historical CPU graphs or verify connection statuses. 

Because this architecture adheres to zero-trust principles, trusting the API Gateway implicitly is considered a security risk. The `SecurityConfig` and `JwtAuthFilter` are implemented locally to intercept all inbound HTTP traffic requesting telemetry data. This ensures that even if an attacker successfully pivots laterally within the internal network, they cannot scrape sensitive historical performance data about the IT fleet without possessing a cryptographically signed JWT. Additionally, to maintain a clean contract with the frontend development team, the `OpenApiConfig` generates live documentation outlining exactly how the aggregated data will be structured when returned by the controller.
