# Role-Based Access Control

Manages granular authorization: mapping Keycloak users (identified by UUID) to roles, permissions, and scoped groups. Evaluated per-request via the internal RBAC API.

### code
**RBAC API Controllers**
[`RbacController.java`](../src/main/java/dev/pulsermm/rbac/api/RbacController.java):
	- `createRole(request)` - Create a new role (name only, permissions added separately via RolePermission table).
	- `assignRole(userId, roleId)` - Assign a role to a user.
	- `listRolesByUser(userId)` - List roles assigned to a user.
	- `listPermissionsByRole(roleId)` - List permissions in a role (with group scope).
	- Permission check: `identity:rbac:manage`.

[`InternalRbacController.java`](../src/main/java/dev/pulsermm/rbac/api/InternalRbacController.java):
	- `checkPermission(userId, permission)` - Resolve a user's permission set (called by API Gateway before routing). Returns set of `ResolvedPermission` (permission name + group scope).
	- No authentication required (internal-only endpoint, called by trusted gateway).

**Application Logic**
[`RbacService.java`](../src/main/java/dev/pulsermm/rbac/application/RbacService.java):
	- `createRole(name)` - Create a new role entity.
	- `assignRoleToUser(userId, roleId)` - Map a user to a role (creates `UserRole` entry).
	- `addPermissionToRole(roleId, permissionId, groupScopeId)` - Add a permission to a role with optional scope.
	- `grantDirectPermission(userId, permissionId, groupScopeId, expiresAt)` - Grant a single permission directly to a user (overrides role-based, with optional TTL).
	- `revokeDirectPermission(userId, permissionId)` - Revoke a direct permission.
	- `removeRoleFromUser(userId, roleId)` - Unassign a role.

[`PermissionEvaluationService.java`](../src/main/java/dev/pulsermm/rbac/application/PermissionEvaluationService.java):
	- `resolve(userId)` - Compute the set of all permissions for a user (from roles + direct permissions). Results cached in Redis (60s TTL).
	- `invalidate(userId)` - Clear cache when roles/permissions change.
	- Evaluated via: user roles → role permissions → union with direct user permissions, filtered by expiry time.

**Domain Entities**
[`Role.java`](../src/main/java/dev/pulsermm/rbac/domain/Role.java):
	- Name, creation timestamp. Maps to `UserRole` (many) and `RolePermission` (many).

[`Permission.java`](../src/main/java/dev/pulsermm/rbac/domain/Permission.java):
	- Immutable atomic action (e.g., `endpoint:view`, `script:execute`). Seeded in `V002__rbac_seed.sql`.

[`UserRole.java`](../src/main/java/dev/pulsermm/rbac/domain/UserRole.java):
	- Composite key entity: (user_id, role_id). Maps Keycloak users to roles.

[`RolePermission.java`](../src/main/java/dev/pulsermm/rbac/domain/RolePermission.java):
	- Composite key entity: (role_id, permission_id, group_scope_id). Links roles to permissions with optional group scoping.

[`UserPermission.java`](../src/main/java/dev/pulsermm/rbac/domain/UserPermission.java):
	- Direct permission grant (user_id, permission_id, group_scope_id, expires_at). Overrides role-based permissions, with optional TTL.

[`EndpointGroup.java`](../src/main/java/dev/pulsermm/rbac/domain/EndpointGroup.java):
	- Organizational unit representing a logical grouping of endpoints (e.g., "Workstations", "Servers"). Scopes permissions.

[`EndpointGroupMembership.java`](../src/main/java/dev/pulsermm/rbac/domain/EndpointGroupMembership.java):
	- Maps endpoints to groups for scope enforcement.

**Infrastructure Repositories**
All repositories handle queries against the authorization graph: `RoleRepository`, `PermissionRepository`, `UserRoleRepository`, `RolePermissionRepository`, `UserPermissionRepository`, `EndpointGroupRepository`, `EndpointGroupMembershipRepository`.

### description
Users (managed by Keycloak, identified by UUID) are authorized via role and permission mapping in the RBAC Service database. A user may have multiple roles (e.g., "Senior Technician" + "Auditor"), each role grants specific permissions, and permissions may be scoped to endpoint groups (e.g., `endpoint:wipe` on group "Servers" only). Direct permission grants allow ad-hoc exceptions (e.g., temporary elevated access).

**Permission resolution:** When the API Gateway receives a request with a JWT (user_id), it calls `InternalRbacController.checkPermission(userId, requiredPermission)`. The controller delegates to `PermissionEvaluationService.resolve(userId)`, which computes all permissions by:
1. Fetching all roles assigned to the user (UserRole table).
2. For each role, fetching all permissions (RolePermission table).
3. Fetching direct permissions (UserPermission table), filtering by expiry time.
4. Returning a set of `ResolvedPermission(permissionName, groupScopeId)` tuples, cached in Redis.

This set is compared against the required permission and scope; if a match exists, the gateway allows the request.

**Group scoping:** Permissions can be scope-agnostic (group_scope_id = null, applies globally) or scoped to specific endpoint groups. When a user is granted `endpoint:wipe` with scope "Servers", they can only wipe endpoints in that group. Scope enforcement is upstream (API Gateway or individual service).

**Configuration:** Permissions are hardcoded in `V002__rbac_seed.sql` and loaded at startup. Roles and assignments are managed via REST API.
