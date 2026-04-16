# API Client & Caching (`src/api/pulseApi.js`)

The single RTK Query client through which all REST traffic flows. It owns the base URL, bearer-token injection, the tag-based cache, and the typed hooks every page consumes.

### code
[`api/pulseApi.js`](../src/api/pulseApi.js):
	- `rawBaseQuery` - A `fetchBaseQuery` pointed at `VITE_API_BASE`. Its `prepareHeaders` calls `keycloak.updateToken(30)` to refresh a near-expiry token (falling back to `keycloak.login()` on failure), then sets `Authorization: Bearer <token>`.
	- `pulseApi` - The `createApi` definition. Declares `tagTypes` (`Endpoint`, `Group`, `TagRule`, `Script`, `Software`, `AlertRule`, `AgentVersion`, `Webhook`, `Users`, `Organizations`, `OrgUsers`) and every `query`/`mutation` endpoint.
	- Query endpoints - Read operations such as `getEndpoints`, `getScripts`, `getSoftware`, `getMetrics`, `getAuditLog`, `listWebhooks`, `getUsers`, `getOrganizations`. Each declares `providesTags` so the cache knows what it holds; volatile data (metrics, processes, files, alerts, deliveries) sets `keepUnusedDataFor: 0` to avoid serving stale reads.
	- Mutation endpoints - Write operations such as `createScript`, `runScript`, `installSoftware`, `createAlertRule`, `publishAgentVersion`, `createWebhook`, `createUser`, `createOrganization`. Each declares `invalidatesTags` so the matching queries refetch automatically.
	- Generated hooks - The named export block (`useGetEndpointsQuery`, `useCreateScriptMutation`, …) exposes one React hook per endpoint; pages import these rather than calling `fetch` directly.

### description
All server communication is funnelled through this one module so that authorization, caching, and invalidation are defined in exactly one place. Pages never construct a request URL or attach a token themselves — they call a generated hook, and the client handles the rest.

Freshness is governed by RTK Query's tag system. A query advertises the tags it provides; a mutation lists the tags it invalidates; when a mutation succeeds, every cached query carrying an invalidated tag is refetched. This keeps the UI consistent without manual refresh wiring — approving a script, for example, invalidates `Script` and the script list updates on its own. Endpoint-scoped resources (software, processes, metrics) tag by `{ type, id }` so invalidating one endpoint's data does not disturb another's. Data that must always be live — metrics, the process snapshot, file listings, audit pages, webhook deliveries — opts out of caching with `keepUnusedDataFor: 0`, while inventory-style data is briefly cached and, on the busiest screens, kept current with a polling interval set at the call site (e.g. the endpoint list polls every 30 s).
</content>
