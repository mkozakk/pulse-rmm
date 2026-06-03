# REST API (`api/controller/`, `application/`, `infrastructure/config/`)

Exposes the alert rule management and alert event endpoints consumed by the webapp. All endpoints under `/api/**` require a valid JWT; the gateway additionally checks the `alert:manage` permission before routing reaches this service.

### code

**Rule management**
[`AlertRuleController.java`](../src/main/java/dev/pulsermm/alert/api/controller/AlertRuleController.java):
- `POST /api/alert-rules` - Validates the request body (name, metricType, operator, threshold, durationSecs, target), creates a rule via `AlertRuleService`, and returns 201 with the persisted rule.
- `GET /api/alert-rules` - Returns all alert rules regardless of enabled state.
- `DELETE /api/alert-rules/{id}` - Hard-deletes the rule; the `ON DELETE CASCADE` on `alert_events.rule_id` removes all associated events automatically.

[`AlertRuleService.java`](../src/main/java/dev/pulsermm/alert/application/AlertRuleService.java):
- `create` - Persists a new `AlertRule` entity. Sets `enabled = true` and `createdAt = now()` in the domain constructor; the creator's UUID is taken from the JWT `sub` claim.
- `list` - Returns all rules via `AlertRuleRepository.findAll()`.
- `delete` - Throws `AlertRuleNotFoundException` (mapped to 404) when the ID does not exist.

**Alert events**
[`AlertEventController.java`](../src/main/java/dev/pulsermm/alert/api/controller/AlertEventController.java):
- `GET /api/alerts?status=open|all` - Lists alert events. `open` (default) returns only events where `acked_at IS NULL`; `all` returns the full history ordered by `triggered_at DESC`.
- `POST /api/alerts/{id}/ack` - Marks the event as acknowledged. Idempotent: acking an already-acked event returns 204 with no change.
- `GET /api/alerts/stream` - SSE stream; see [`sse.md`](sse.md).

[`AlertEventService.java`](../src/main/java/dev/pulsermm/alert/application/AlertEventService.java):
- `listOpen` / `listAll` - Delegates to `AlertEventRepository` named queries.
- `ack` - Loads the event by ID and calls `event.ack(userId)` only when `event.isOpen()` is true, then saves. The idempotency check is a no-op when already acked.

**DTOs**
[`CreateAlertRuleRequest.java`](../src/main/java/dev/pulsermm/alert/api/dto/CreateAlertRuleRequest.java):
- Bean Validation constraints: `name` (not blank, max 120), `metricType` (pattern `cpu|ram|disk`), `operator` (pattern `>|<`), `threshold` (0..100), `durationSecs` (30..3600), `target.type` (pattern `group|tag`), `target.value` (not blank, max 200).

[`AlertRuleResponse.java`](../src/main/java/dev/pulsermm/alert/api/dto/AlertRuleResponse.java) & [`AlertEventResponse.java`](../src/main/java/dev/pulsermm/alert/api/dto/AlertEventResponse.java):
- Flat records mapping entity fields to JSON. `AlertEventResponse` includes `ruleName` denormalized from the joined `AlertRule`.

**Security**
[`SecurityConfig.java`](../src/main/java/dev/pulsermm/alert/infrastructure/config/SecurityConfig.java):
- Stateless session policy; permits `/actuator/**` and `/v3/api-docs` without auth; requires `fullyAuthenticated` for all `/api/**` paths.

[`JwtAuthFilter.java`](../src/main/java/dev/pulsermm/alert/api/JwtAuthFilter.java):
- Validates the JWT from the `Authorization: Bearer` header (or `?token=` query param for SSE). Populates the `SecurityContext` with the user's UUID as the principal name.

[`GlobalExceptionHandler.java`](../src/main/java/dev/pulsermm/alert/api/errors/GlobalExceptionHandler.java):
- Maps `MethodArgumentNotValidException` → 400 with RFC 7807 `ProblemDetail` including a field-level `errors` map.
- Maps `AlertRuleNotFoundException` → 404.

### description

Permission enforcement is split between two layers. The API gateway's `AlertPermissionFilter` checks the `alert:manage` permission against the identity service before routing traffic to this service, returning 401/403 at the edge for any request lacking the permission. The service's own `SecurityConfig` and `JwtAuthFilter` provide a second independent layer: they validate the JWT signature locally without calling any external service, so unauthorized requests that somehow bypass the gateway are still rejected.

The `target` field in the create request uses a nested record (`TargetSpec`) that is flattened to `targetType` and `targetValue` columns in the database and flattened back out in `AlertRuleResponse`. This avoids a join table for what is logically a two-field struct while keeping the API ergonomic for the caller.
