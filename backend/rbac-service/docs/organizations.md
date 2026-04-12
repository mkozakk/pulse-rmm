# Organizations (Multi-Tenancy)

RBAC Service models the system as a multi-tenant platform where each organization is an isolated tenant. Organizations scope users, roles, and permissions.

### code
**Organizations API**
[`OrganizationController.java`](../src/main/java/dev/pulsermm/rbac/api/OrganizationController.java):
	- `create(request)` - Create a new organization (name, global admin only). Returns 201 with Location header.
	- `list()` - List all organizations (global admin only).
	- `get(id)` - Fetch a single organization.
	- `update(id, request)` - Update organization name (conflict if name already exists).
	- `delete(id)` - Delete an organization (only if empty - no users in Keycloak with `org_id` attribute).
	- `createUser(orgId, request)` - Create a user scoped to this organization. Calls `KeycloakAdminClient.createUser()` with org_id set. Optionally assigns role.

**Organization Service**
[`OrganizationService.java`](../src/main/java/dev/pulsermm/rbac/application/OrganizationService.java):
	- `create(name)` - Create org entity. Throws `ConflictException` if name exists.
	- `get(id)`, `list()` - Query organizations.
	- `update(id, name)` - Rename organization. Checks uniqueness.
	- `delete(id)` - Delete org. Throws `ConflictException` if `KeycloakAdminClient.listUsersByOrg(id)` is not empty.

**Organization Domain**
[`Organization.java`](../src/main/java/dev/pulsermm/rbac/domain/Organization.java):
	- Entity: `id` (UUID PK), `name` (UNIQUE), `created_at`.
	- Table: `organizations` (seeded with default org in `V004__organizations.sql`).

**Keycloak Integration**
- Users are stored in Keycloak with a custom `org_id` attribute set during creation.
- `KeycloakAdminClient.listUsersByOrg(UUID orgId)` queries Keycloak's attribute index: `?q=org_id:{uuid}`.
- Organization deletion checks if any Keycloak users still carry the org_id attribute.

### description
Multi-tenancy is achieved via organization entities in the database and custom `org_id` attributes in Keycloak. When a user is created via `POST /api/organizations/{orgId}/users`, the org_id is set as a Keycloak custom attribute, allowing admins to query "which users belong to org X" and "can org X be deleted?"

**Permission scoping:** Org admin (role with permission scoped to a group) can manage users within their org. Global admin can manage any org. Org admins cannot create other org admins (enforced in UserController).

**Database schema:**
```sql
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**API Endpoints:**
```
POST   /api/organizations                  201 Created {id, name, createdAt}
GET    /api/organizations                  200 OK [{...}, {...}]
GET    /api/organizations/{id}             200 OK {id, name, createdAt}
PUT    /api/organizations/{id}             200 OK {id, name, createdAt}
DELETE /api/organizations/{id}             204 No Content (fails 409 if non-empty)
POST   /api/organizations/{orgId}/users    201 Created {id, username, email, ...}
```

**Example flow:**
1. Global admin calls `POST /api/organizations` with `{name: "ACME Corp"}` → org created with UUID.
2. Global admin calls `POST /api/organizations/{uuid}/users` with user data → user created in Keycloak with `org_id` attribute.
3. Organization admin queries `GET /api/identity/users` → sees only users in their org (via `callerOrg(jwt)` in UserController).
4. Organization admin calls `POST /api/organizations/{uuid}/users` → succeeds (same org) or 403 (different org).
5. Global admin calls `DELETE /api/organizations/{uuid}` → succeeds only if no users in Keycloak have `org_id={uuid}`.
