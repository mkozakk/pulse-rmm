# Authentication via Keycloak

User authentication is delegated to Keycloak, a centralized identity and access management system. RBAC Service manages authorization (roles and permissions) but does not handle credential verification.

### code
**Keycloak Admin Client**
[`KeycloakAdminClient.java`](../src/main/java/dev/pulsermm/rbac/infrastructure/keycloak/KeycloakAdminClient.java):
	- `listUsers()` - Fetch all users from Keycloak realm.
	- `listUsersByOrg(UUID orgId)` - Query users by custom `org_id` attribute (attribute search).
	- `createUser(username, email, firstName, lastName, password, orgId)` - Create a new user in Keycloak and set custom `org_id` attribute.
	- `getUser(UUID userId)` - Fetch a single user by UUID.
	- `updateUser(userId, email, firstName, lastName, enabled)` - Update user profile.
	- `resetPassword(userId, newPassword)` - Force password change.
	- `deleteUser(UUID userId)` - Soft-delete a user from the realm.
	- Token management via `withToken()` helper - acquires Bearer token for admin API calls (cached, auto-refreshed on expiry).

[`KeycloakUser.java`](../src/main/java/dev/pulsermm/rbac/infrastructure/keycloak/KeycloakUser.java):
	- Record wrapping Keycloak user data: `id`, `username`, `email`, `firstName`, `lastName`, `enabled`, `createdAt`, `org_id`.

**User Management Controllers**
[`UserController.java`](../src/main/java/dev/pulsermm/rbac/api/UserController.java):
	- `listUsers()` - List all users (global admin) or users in caller's organization (org admin).
	- `getUser(id)` - Fetch a single user.
	- `createUser(request)` - Create a new user in Keycloak and optionally assign a role.
	- `updateUser(id, request)` - Update email/name/enabled/password.
	- `deleteUser(id)` - Delete a user.
	- `assignRoles(id, request)` - Modify user's role assignments via `RbacService`.
	- Permission check: `identity:user:manage` (verify via `PermissionEvaluationService`).

[`OrganizationController.java`](../src/main/java/dev/pulsermm/rbac/api/OrganizationController.java):
	- `createUser(orgId, request)` - Create a user scoped to a specific organization (sets `org_id` attribute in Keycloak).
	- Users created here are org-scoped; only global admins can access `OrganizationController`.

**Configuration**
[`SecurityConfig.java`](../src/main/java/dev/pulsermm/rbac/config/SecurityConfig.java):
	- Defines public routes (`/actuator/health`) and protected routes (`/api/organizations`, `/api/identity/users`).
	- `IdentityJwtAuthFilter` validates JWT for direct requests to this service (local JWT validation, not delegated to Keycloak).
	- Routes requiring `Bearer` token validated against `${keycloak.url}` public key certificate.

### description
Keycloak is the system's centralized identity provider, managing user credentials, MFA, and account lifecycle. RBAC Service never stores passwords or private keys. Instead, it delegates authentication to Keycloak and manages authorization separately.

**User creation flow:** Admin calls `POST /api/identity/users` with credentials → `UserController` calls `KeycloakAdminClient.createUser()` → Keycloak creates user and returns UUID → service optionally assigns role via `RbacService` → returns `UserResponse` DTO with public data only (no credentials).

**Organization scoping:** When creating a user via `POST /api/organizations/{orgId}/users`, the `org_id` custom attribute is set in Keycloak, allowing `KeycloakAdminClient.listUsersByOrg()` to filter users by organization. Org admin cannot create global admin users (enforced in `UserController`).

**Direct API calls to this service:** JWT is validated locally via `IdentityJwtAuthFilter`, which extracts the Bearer token and verifies it against Keycloak's public key (fetched from OIDC endpoint). This allows services and the gateway to validate tokens without calling Keycloak synchronously on every request.

**Configuration:** Keycloak URL (`keycloak.url`), realm (`pulse-rmm`), and admin credentials (`keycloak.admin.username`, `keycloak.admin.password`) must be set via environment variables. The admin client caches the Bearer token and auto-refreshes on expiry.
