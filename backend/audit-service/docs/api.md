# Query & Export API (`api/controller/`, `application/`)

Exposes a filtered, paginated query endpoint and streaming export for the audit log. All write operations are rejected at the controller level to enforce immutability.

### code
**Controller**
[`AuditController.java`](../src/main/java/dev/pulsermm/audit/api/controller/AuditController.java):
	- `list` - Accepts optional `user`, `endpoint`, `from`, and `to` query parameters and delegates to `AuditQueryService`. Page size is capped at 200 to prevent unbounded result sets.
	- `export` - Returns a `StreamingResponseBody` so records are written directly to the HTTP response output stream in batches — the full result set is never held in memory. Supports `format=csv` (default) and `format=json` (NDJSON).
	- `deleteNotAllowed` - Catches all `DELETE /**` requests and returns 403, making the immutability contract explicit rather than relying on the absence of a handler.

**Application Services**
[`AuditQueryService.java`](../src/main/java/dev/pulsermm/audit/application/AuditQueryService.java):
	- `list` - Thin read-only transactional wrapper around the repository filter query, keeping the controller free of persistence concerns.

[`AuditExportService.java`](../src/main/java/dev/pulsermm/audit/application/AuditExportService.java):
	- `streamCsv` & `streamNdjson` - Iterate through 500-record pages returned by the repository, serialising and flushing each batch before fetching the next. CSV output includes proper quoting (RFC 4180) for fields that contain commas, quotes, or newlines.

**Repository**
[`AuditEventRepository.java`](../src/main/java/dev/pulsermm/audit/infrastructure/persistence/AuditEventRepository.java):
	- `findFiltered` - JPQL query with nullable parameters: any `null` filter is treated as "no restriction", so the same query handles all filter combinations without dynamic query building. Results are ordered by `created_at DESC`.

### description
The audit log is append-only by design. Rather than relying on database-level access controls alone, the controller explicitly returns 403 for any destructive HTTP method. The export endpoints stream data directly to the client using Spring's `StreamingResponseBody`, which hands the `OutputStream` to a virtual thread while the servlet container stays non-blocking. This allows arbitrarily large exports without memory pressure. The NDJSON format is preferred for machine consumption because each line is a self-contained JSON object that can be parsed incrementally; CSV is offered for compatibility with spreadsheet tooling.

### endpoints
```
GET  /api/audit                       200 Page<AuditEventResponse>
GET  /api/audit/export?format=csv     200 text/csv (attachment)
GET  /api/audit/export?format=json    200 application/x-ndjson (attachment)
DELETE /api/audit/**                  403 (always)
```
