# Authentication & Session (`src/keycloak.js`, `src/store/authSlice.js`)

Delegates identity to Keycloak over OIDC with PKCE, holds the access token in Redux, and keeps it fresh for the lifetime of the session.

### code
[`keycloak.js`](../src/keycloak.js):
	- Module export - Constructs a single `Keycloak` client configured with the realm (`pulse-rmm`), the public client id (`pulse-webapp`), and the Keycloak base URL from `VITE_KEYCLOAK_URL`. Exported as a singleton so every module shares one session.

[`store/authSlice.js`](../src/store/authSlice.js):
	- `setCredentials` - Stores the latest access token in `state.token`.
	- `clearCredentials` - Nulls the token on logout.
	- `setInitialized` - Flips `state.initialized` to `true` once Keycloak has finished its initial handshake, which `ProtectedRoute` waits on before rendering.

Global-admin detection (used in `components/AppShell.jsx` and `pages/UsersPage.jsx`):
	- `keycloak.tokenParsed?.org_id` - A user with no `org_id` claim is treated as a **global admin** (cross-organization). A user with an `org_id` is scoped to that single organization. This single check toggles the Organizations nav item and the org-selection controls in the user forms.

### description
The webapp is a public OIDC client and never sees a password — login happens entirely on the Keycloak-hosted page, and the app receives only the resulting tokens. PKCE (`S256`) protects the authorization-code exchange, which is the recommended flow for browser apps that cannot keep a client secret.

Token lifecycle is split between Keycloak and Redux. Keycloak owns refresh and rotation; the `auth` slice is just the current snapshot that the rest of the app reads. Two places refresh the token before use: the Keycloak `onTokenExpired` callback wired in `main.jsx`, and the RTK Query base query, which calls `keycloak.updateToken(30)` before every REST request so a token that is within 30 seconds of expiry is renewed rather than sent stale. The `org_id` claim drives multi-tenancy in the UI: it is the same boundary the backend enforces, surfaced client-side only to hide controls a user cannot use — the API Gateway re-checks authorization on every request regardless of what the UI shows.
</content>
