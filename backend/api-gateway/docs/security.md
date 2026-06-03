# Security & Permission Filtering (`api/`, `infrastructure/identity/`)

Enforces perimeter authentication via JWT and role-based access control across all incoming traffic, acting as a protective barrier for internal microservices.

### code
**Configuration & Core**
[`SecurityConfig.java`](../src/main/java/dev/pulsermm/gateway/api/SecurityConfig.java):
	- `filterChain` - Establishes the perimeter security context and configures the filter chain: rate limiting, then permission checks (structure, alerts, API), then org context injection. Stateless sessions (no cookies for auth), CORS enabled, CSRF disabled.
	- Configures OAuth2 resource server with JWT validation via Bearer token.
	- Applies rate limiting (bucket4j) on a per-user basis.

**Permission Filtering**
[`StructurePermissionFilter.java`](../src/main/java/dev/pulsermm/gateway/api/StructurePermissionFilter.java):
	- `doFilterInternal` - Blocks write operations on groups, tags, and endpoint assignments unless the user has `endpoint:structure:manage` permission. Prevents unauthorized organizational changes.

[`AlertPermissionFilter.java`](../src/main/java/dev/pulsermm/gateway/api/AlertPermissionFilter.java):
	- `doFilterInternal` - Blocks all access to `/api/alert-rules` and `/api/alerts` unless the user has `alert:manage` permission. Returns 401 if unauthenticated, 403 if unauthorized.

[`ApiPermissionFilter.java`](../src/main/java/dev/pulsermm/gateway/api/ApiPermissionFilter.java):
	- `doFilterInternal` - Main RBAC enforcement filter for all monitored endpoints (`/api/endpoints`, `/api/scripts`, `/api/audit`, `/api/agent-versions`, `/api/webhooks`, `/api/deliveries`, `/api/enrolment`, `/api/groups`, `/api/tag-rules`).
	- Permits certain paths without auth (`POST /api/scripts/runs/*/endpoints/*/ack` for agent callback, `GET /api/agent-versions/checksum` for install script).
	- Checks granular permissions: endpoint-scoped checks (file browse, software view/manage, process act) by endpoint ID, global checks (script view/create/approve, audit view/export, agent/integration/enrolment management).
	- Delegates to `PermissionGuard` for each check.

[`PermissionGuard.java`](../src/main/java/dev/pulsermm/gateway/api/PermissionGuard.java):
	- `canViewEndpoint` / `canActOnEndpoint` / `canViewSoftware` / `canManageSoftware` / `canBrowseFiles` / `canOpenShell` - Endpoint-scoped permission checks. Fetches the endpoint's group and checks if the user has the required permission scoped to that group.
	- `canViewEndpoints` / `canViewScripts` / `canCreateScripts` / `canApproveScripts` / `canViewAudit` / `canExportAudit` / `canManageAgentVersions` / `canManageIntegrations` / `canManageEnrolment` - Global permission checks (no group scope). Used for list endpoints, script operations, audit access, and system admin tasks.
	- Delegates to `IdentityClient` to fetch user permissions and `PermissionChecker` to evaluate them against the required permission and group scope.

**Org Context Injection**
[`OrgContextFilter.java`](../src/main/java/dev/pulsermm/gateway/api/OrgContextFilter.java):
	- `doFilterInternal` - Derives the caller's organization from the JWT `org_id` claim, validates that any endpoint in the path belongs to that org, and injects a trusted `X-User-Org-Id` header into forwarded requests.
	- For org-scoped users, enforces 404 (not 403) if the endpoint does not belong to their org, preventing org users from probing other orgs' endpoints.
	- Global admins (no `org_id` claim) receive no header - downstream services apply no org filtering.

[`OrgHeaderRequestWrapper.java`](../src/main/java/dev/pulsermm/gateway/api/OrgHeaderRequestWrapper.java):
	- Wraps the request to hide any client-supplied `X-User-Org-Id` header and replace it with the trusted value derived from the JWT. Ensures downstream services always use the gateway-determined org.

**Identity Verification**
[`EndpointOrgClient.java`](../src/main/java/dev/pulsermm/gateway/infrastructure/identity/EndpointOrgClient.java):
	- `getEndpointOrg` - Internal REST client that calls the endpoint-service's private `/internal/endpoints/{id}/org` endpoint to determine which org owns a given endpoint. Returns empty if the endpoint does not exist or has no org assignment.

### description
The API Gateway enforces security in two layers: **authentication** and **authorization**.

**Authentication** happens first via `SecurityConfig`: OAuth2 with JWT Bearer tokens. The token signature is verified by Spring Security's embedded JWT decoder. No token = no authentication context.

**Authorization** (RBAC) happens via a chain of permission filters:
1. `StructurePermissionFilter` - Blocks changes to groups and tags unless user has global `endpoint:structure:manage`.
2. `AlertPermissionFilter` - Blocks all alert operations unless user has global `alert:manage`.
3. `ApiPermissionFilter` - Main filter that routes to endpoint-scoped and global permission checks via `PermissionGuard`.
4. `PermissionGuard` - Queries the identity-service to fetch the user's permissions, then checks if they have the required permission for the requested action (scoped to the endpoint's group if applicable).

**Organization filtering** (`OrgContextFilter`) runs after permission checks. It extracts the `org_id` from the JWT, validates that any endpoint in the request path belongs to that org (returning 404 to prevent org boundary probing), and injects a trusted `X-User-Org-Id` header into the request. Downstream services use this header to scope their own queries and responses to the caller's org. Global admins have no `org_id` and get no header, so they see all orgs.

**Permit-all paths** (agent ack callback, checksum fetch) are explicitly allowed without auth. These are internal operations initiated by the gateway or install scripts, not user-facing.
