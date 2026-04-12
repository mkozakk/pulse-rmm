# Request Routing & Rate Limiting (`api/`, `config/`)

The API Gateway is a request router and security perimeter, not a gRPC server. It does not implement agent communication - that is handled by individual microservices that expose gRPC endpoints directly.

### code
**Routing & Rate Limiting**
[`SecurityConfig.java`](../src/main/java/dev/pulsermm/gateway/api/SecurityConfig.java):
	- `filterChain` - Configures the filter chain that runs on every HTTP request:
		1. CORS headers (via Spring's CORS configuration)
		2. Rate limiting (bucket4j, per-user)
		3. Permission filters (structure, alerts, API)
		4. Org context injection
		5. Forward to downstream service

[`OpenApiConfig.java`](../src/main/java/dev/pulsermm/gateway/config/OpenApiConfig.java):
	- Configures Springdoc OpenAPI for Swagger UI generation (if present). Auto-generates `/swagger-ui.html` and `/v3/api-docs` from REST controller annotations in downstream services.

### description
The API Gateway is the single entry point for all user-facing traffic. It is a pure **request router**: it authenticates the caller via JWT, checks RBAC permissions via filters, injects organization context, applies rate limiting, and forwards the request to the appropriate downstream microservice.

**It does NOT:**
- Host gRPC services for agent communication
- Maintain agent registries or stream state
- Implement command dispatchers or observers
- Hold real-time bidirectional channels

**Each microservice that needs to communicate with agents** (e.g., endpoint-service for remote sessions, script-service for command delivery) implements its own gRPC listener and agent management logic. The agent dials into those services directly, not the gateway.

The gateway's role is strictly HTTP: routes `/api/scripts` to the script-service, `/api/endpoints` to the endpoint-service, rate-limits requests, and ensures callers are authorized. The downstream services handle the complexity of persistent agent connections and gRPC.
