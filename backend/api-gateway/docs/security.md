# Security & RBAC (`api/`, `infrastructure/identity/`, `/`)

Enforces perimeter authentication and role-based access control across all incoming traffic, acting as a protective barrier for internal microservices.

### code
**Configuration & Core**
[`ApiGatewayApplication.java`](../src/main/java/dev/pulsermm/gateway/ApiGatewayApplication.java):
	- `main` - The core entry point that bootstraps the reactive Spring Cloud Gateway context, allowing it to begin intercepting and routing network traffic.
[`SecurityConfig.java`](../src/main/java/dev/pulsermm/gateway/api/SecurityConfig.java):
	- `securityWebFilterChain` - Establishes the perimeter security context, distinguishing between public routes, protected REST endpoints, and WebSocket upgrades, ensuring the authentication filter is applied correctly to all incoming requests.

**Authentication & Authorization Filters**
[`JwtAuthFilter.java`](../src/main/java/dev/pulsermm/gateway/api/JwtAuthFilter.java):
	- `filter` - Intercepts inbound HTTP requests from the frontend, parsing the Authorization header to cryptographically validate JSON Web Tokens and establish the user's security context before routing proceeds.
[`StructurePermissionFilter.java`](../src/main/java/dev/pulsermm/gateway/api/StructurePermissionFilter.java):
	- `filter` - Acts as a specialized enforcement layer that evaluates organizational boundaries, guaranteeing a user cannot interact with an endpoint if they lack rights to that endpoint's specific group.
[`PermissionGuard.java`](../src/main/java/dev/pulsermm/gateway/api/PermissionGuard.java):
	- `checkAccess` - Serves as the primary barrier within the routing chain, actively pausing the request to confirm explicit, atomic permissions before permitting the traffic to reach an internal microservice.

**Identity Verification Logic**
[`PermissionChecker.java`](../src/main/java/dev/pulsermm/gateway/api/PermissionChecker.java):
	- `hasPermission` - Orchestrates the internal validation workflow by invoking the external Identity Service client to determine if the active user possesses the required rights for the requested action.
[`IdentityClient.java`](../src/main/java/dev/pulsermm/gateway/infrastructure/identity/IdentityClient.java):
	- `checkPermission` - An internal HTTP client dedicated solely to communicating with the Identity Service's highly optimized, private RBAC resolution endpoint.
[`ResolvedPermission.java`](../src/main/java/dev/pulsermm/gateway/api/ResolvedPermission.java):
	- `ResolvedPermission` - A lightweight internal record that structures the definitive affirmative or negative authorization response returned by the Identity Service.

### description
Because the API Gateway serves as the single entry point to the entire backend cluster, it bears the responsibility of securing the perimeter. Rather than forcing every individual microservice to implement redundant authentication logic, security is enforced entirely at the edge. The `ApiGatewayApplication` spins up the reactive web server, and all incoming traffic immediately encounters the rules defined in `SecurityConfig`. The `JwtAuthFilter` extracts the authorization header, verifies the token signature, and extracts the user's ID. However, mere authentication is insufficient. To enforce Role-Based Access Control, the gateway utilizes the `PermissionGuard` and `StructurePermissionFilter`. Before forwarding any request (like querying metrics or enrolling an agent), these guards halt the routing sequence and invoke the `PermissionChecker`. The checker utilizes the `IdentityClient` to make a rapid, internal HTTP call to the private Identity Service. The Identity Service evaluates the complex web of roles and scopes, returning a `ResolvedPermission` payload. Only if this payload explicitly grants access does the Gateway resume routing; otherwise, it immediately returns a 403 Forbidden response, ensuring unauthorized traffic never pollutes the internal network.
