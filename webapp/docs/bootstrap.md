# Bootstrap, Routing & Error Handling (`src/main.jsx`, `src/App.jsx`)

How the single-page app boots, mounts the router behind authentication, and contains render-time failures.

### code
[`main.jsx`](../src/main.jsx):
	- Module body - The entry point. Renders a temporary "Initialising…" placeholder, then calls `keycloak.init` with `onLoad: 'login-required'`, `pkceMethod: 'S256'`, and `checkLoginIframe: false`.
	- `init().then` callback - On success, dispatches the access token into the Redux `auth` slice (`setCredentials`), marks auth initialised (`setInitialized`), wires the Keycloak refresh/logout/expiry callbacks, and finally mounts `<App />` wrapped in `<Provider>` (Redux), `<ErrorBoundary>`, and `<StrictMode>`.

[`App.jsx`](../src/App.jsx):
	- `App` - Declares the `BrowserRouter` and the full route table. All real routes are nested under a single `<Route element={<ProtectedRoute />}>` so the guard runs once for every page. `/` and any unmatched path redirect to `/endpoints`.

[`components/ProtectedRoute.jsx`](../src/components/ProtectedRoute.jsx):
	- `ProtectedRoute` - Reads `auth.initialized` and `auth.token` from the store. Renders nothing until Keycloak has resolved; if there is no token it triggers `keycloak.login()`; otherwise it renders the matched child route via `<Outlet />`.

[`components/ErrorBoundary.jsx`](../src/components/ErrorBoundary.jsx):
	- `ErrorBoundary` - A class component using `getDerivedStateFromError` to catch any render exception in the tree below it. On error it shows a "Something went wrong" panel with the error message and a "Reload page" button instead of a blank screen.

### description
The app deliberately blocks first paint on authentication. `main.jsx` does not render the application until `keycloak.init` resolves; with `onLoad: 'login-required'` an unauthenticated visitor is redirected to the Keycloak login page before any React route mounts. Once a session exists, the token is pushed into Redux and three Keycloak callbacks keep it current for the rest of the session: `onAuthRefreshSuccess` re-publishes the rotated token, `onAuthLogout` clears it, and `onTokenExpired` attempts a silent refresh and falls back to a full login.

Routing is intentionally flat — one router, one guard. Because every page is nested under `ProtectedRoute`, no individual page has to check authentication itself; the guard short-circuits rendering until the store reports both `initialized` and a `token`. The `ErrorBoundary` sits just below the Redux provider so a thrown error in any page degrades to a recoverable message rather than crashing the whole console.
</content>
