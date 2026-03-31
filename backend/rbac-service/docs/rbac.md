# Role-Based Access Control (`api/`, `application/`, `domain/`, `infrastructure/`)

Defines the granular authorization structures regulating access to endpoints and features, encompassing the entire entity graph of roles, permissions, users, and scoping groups.

### code
**API & Routing**
[`RbacController.java`](../src/main/java/dev/pulsermm/identity/api/RbacController.java):
	- `createRole` & `assignRole` - Provide standard administrative REST endpoints for managing the system's role configurations and assigning them to user accounts.
[`InternalRbacController.java`](../src/main/java/dev/pulsermm/identity/api/InternalRbacController.java):
	- `checkPermission` - Exposes an internal, highly optimized HTTP endpoint designed explicitly for the API Gateway to rapidly query a user's rights during real-time traffic routing.
[`IdentityJwtAuthFilter.java`](../src/main/java/dev/pulsermm/identity/api/IdentityJwtAuthFilter.java):
	- `doFilterInternal` - A localized security filter that extracts JWTs from incoming requests directly addressing the Identity Service (e.g., when an admin modifies roles), establishing a local security context independent of the API Gateway's perimeter checks.

**Data Transfer Objects (DTOs)**
[`CreateRoleRequest.java`](../src/main/java/dev/pulsermm/identity/api/dto/CreateRoleRequest.java) & [`ResolvedPermission.java`](../src/main/java/dev/pulsermm/identity/api/dto/ResolvedPermission.java):
	- `CreateRoleRequest` captures the inbound payload when an admin defines a new job function. `ResolvedPermission` is the strict outbound format the internal controller uses to inform the Gateway if an action is authorized.

**Application Logic**
[`RbacService.java`](../src/main/java/dev/pulsermm/identity/application/RbacService.java):
	- `assignRoleToUser` & `createRole` - Manage the complex logic of validating role definitions, persisting them, and mapping them to existing user accounts.
[`PermissionEvaluationService.java`](../src/main/java/dev/pulsermm/identity/application/PermissionEvaluationService.java):
	- `hasPermission` - The core engine that evaluates the interconnected graph of a user's roles, direct permissions, and group scopes to determine if a specific atomic action is permitted.

**Domain Entities**
[`Role.java`](../src/main/java/dev/pulsermm/identity/domain/Role.java) & [`Permission.java`](../src/main/java/dev/pulsermm/identity/domain/Permission.java):
	- `Role` represents a collection of permissions logically grouped for a job function. `Permission` represents the smallest, immutable atomic right required to execute a distinct action within the system.
[`UserRole.java`](../src/main/java/dev/pulsermm/identity/domain/UserRole.java) & [`UserRoleId.java`](../src/main/java/dev/pulsermm/identity/domain/UserRoleId.java):
	- Map users to broad roles using a composite key entity.
[`RolePermission.java`](../src/main/java/dev/pulsermm/identity/domain/RolePermission.java) & [`RolePermissionId.java`](../src/main/java/dev/pulsermm/identity/domain/RolePermissionId.java):
	- Map roles to specific atomic permissions using a composite key entity.
[`UserPermission.java`](../src/main/java/dev/pulsermm/identity/domain/UserPermission.java) & [`UserPermissionId.java`](../src/main/java/dev/pulsermm/identity/domain/UserPermissionId.java):
	- Allow for explicit, granular overrides granting specific atomic permissions directly to a user, bypassing role assignment.
[`EndpointGroup.java`](../src/main/java/dev/pulsermm/identity/domain/EndpointGroup.java) & [`EndpointGroupMembership.java`](../src/main/java/dev/pulsermm/identity/domain/EndpointGroupMembership.java):
	- Represent organizational scoping boundaries, allowing the system to grant a user administrative rights over "Workstations" without granting them access to "Servers."

**Infrastructure Repositories**
[`RoleRepository.java`](../src/main/java/dev/pulsermm/identity/infrastructure/RoleRepository.java), [`PermissionRepository.java`](../src/main/java/dev/pulsermm/identity/infrastructure/PermissionRepository.java):
	- Handle the storage and retrieval of the fundamental authorization structures.
[`UserRoleRepository.java`](../src/main/java/dev/pulsermm/identity/infrastructure/UserRoleRepository.java), [`RolePermissionRepository.java`](../src/main/java/dev/pulsermm/identity/infrastructure/RolePermissionRepository.java), [`UserPermissionRepository.java`](../src/main/java/dev/pulsermm/identity/infrastructure/UserPermissionRepository.java):
	- Provide optimized queries for resolving the complex mapping tables that define the actual authorization graph.
[`EndpointGroupRepository.java`](../src/main/java/dev/pulsermm/identity/infrastructure/EndpointGroupRepository.java), [`EndpointGroupMembershipRepository.java`](../src/main/java/dev/pulsermm/identity/infrastructure/EndpointGroupMembershipRepository.java):
	- Manage the persistence of organizational scopes and their mappings.

### description
Enterprise applications demand precise control over who can perform what actions, which the Identity Service enforces using a strict Role-Based Access Control (RBAC) model. At the database layer, `Permission` entities define absolute, hardcoded system actions (like `script:execute`). These are aggregated into `Role` entities using the `RolePermission` mapping table to represent business functions. When a `User` is created, the `RbacService` maps them to one or more roles via the `UserRole` table, or grants them explicit overrides via the `UserPermission` table. Furthermore, access can be geographically or logically scoped using `EndpointGroup` structures, ensuring a technician might have administrative rights, but only over a specific subset of the fleet. Because the API Gateway must authorize every single incoming request to the entire infrastructure, it requires a mechanism to quickly translate a JWT user ID into an affirmative or negative permission check. The Gateway queries the `InternalRbacController`, which delegates the query to the `PermissionEvaluationService`. This engine transverses the complex web of repositories to evaluate the user's mapped roles, atomic permissions, and group memberships, ultimately returning a definitive `ResolvedPermission` DTO to either allow or block the network traffic.
