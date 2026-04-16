# Audit Log (`src/pages/AuditPage.jsx`)

A filterable, paginated, exportable view over the immutable audit trail.

### code
[`pages/AuditPage.jsx`](../src/pages/AuditPage.jsx):
	- `AuditPage` - Holds a draft filter set (user, endpoint, from/to dates) separate from the applied set, so changing inputs does not refetch until the operator searches. Reads pages with `useGetAuditLogQuery({ ...applied, page })`.
	- Filter dropdowns - Populated from `useGetUsersQuery` and `useGetEndpointsQuery` so the operator filters by known actors and machines rather than free text.
	- Pagination - Server-side; `page` is part of the query key and `data.totalPages` drives the controls.
	- Row expansion - Each event row expands to reveal its full detail payload.

### description
The audit page treats the log as the system of record it is: read-only, paginated on the server, and never cached (`keepUnusedDataFor: 0`) so it always shows the authoritative trail. The deliberate split between *draft* filters and *applied* filters means typing into the date or user fields does not hammer the backend — the query only re-runs when the operator explicitly searches, and the active filters and page are encoded in the query key so the cache keys stay distinct per view.

Filters are populated from the real user and endpoint lists rather than free-text matching, which keeps queries precise and the result set meaningful for compliance review. Each row can be expanded to inspect the full event payload, and the page offers an export of the current view.
</content>
