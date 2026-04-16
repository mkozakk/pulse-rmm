# Users & Organizations (`src/pages/UsersPage.jsx`, `src/pages/OrganizationsPage.jsx`)

User and role management, plus the global-admin console for managing organizations and their members.

### code
[`pages/UsersPage.jsx`](../src/pages/UsersPage.jsx):
	- `UsersPage` - Lists users (`useGetUsersQuery`) and creates, edits, deletes, and re-roles them (`useCreateUserMutation`, `useUpdateUserMutation`, `useDeleteUserMutation`, `useUpdateUserRolesMutation`); role choices come from `useGetRolesQuery`.
	- `isGlobalAdmin` - `!keycloak.tokenParsed?.org_id`. A global admin must pick a target organization when creating a user (orgs from `useGetOrganizationsQuery`) and creates them via `useCreateOrgUserMutation`; an org-scoped admin creates users only within their own org.

[`pages/OrganizationsPage.jsx`](../src/pages/OrganizationsPage.jsx):
	- `OrganizationsPage` - Global-admin only. Lists organizations (`useGetOrganizationsQuery`), creates and deletes them (`useCreateOrganizationMutation`, `useDeleteOrganizationMutation`).
	- `OrgUsersPanel` - A side panel listing one org's members (`useGetOrgUsersQuery`) and adding a member with an assigned role (`useCreateOrgUserMutation`).

### description
These two pages implement the same multi-tenant boundary the backend enforces, surfaced through the single `org_id` check. An org-scoped administrator manages only their own organization's users and never sees the Organizations screen at all — it is not even added to the navigation (see [Application Shell & Navigation](layout.md)). A global administrator works across tenants: when they create a user they must choose which organization it belongs to, which is why the user form swaps in an org selector for them.

The Organizations page is the global admin's tenant console: create or remove organizations, and through `OrgUsersPanel` seed each one with its first members and roles. This is purely a convenience surface over the RBAC service — every action is re-authorized server-side, so hiding a control in the UI is a usability choice, never the security boundary. The identity model behind these screens is owned by the [RBAC Service](../../backend/rbac-service/README.md).
</content>
