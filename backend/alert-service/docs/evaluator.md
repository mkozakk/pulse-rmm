# Alerting Evaluator (`application/`, `infrastructure/`)

Runs a scheduled evaluation loop every 30 seconds, checking every enabled alert rule against the time-series metric data in TimescaleDB. When a condition has held for the full configured duration, an alert event is persisted and published. When the condition fully clears, previously-open events are marked as cleared so the rule can re-fire after acknowledgement.

### code

**Scheduled loop**
[`AlertEvaluator.java`](../src/main/java/dev/pulsermm/alert/application/AlertEvaluator.java):
- `evaluate` — The entry point, called every 30 seconds via `@Scheduled(fixedDelay = 30_000)`. Loads all enabled rules and, for each rule, resolves target endpoints before delegating to `evaluateRuleForEndpoint`.
- `evaluateRuleForEndpoint` — Runs the breach-count query for a single `(rule, endpoint)` pair. If the condition holds, attempts to insert a new event. If the condition has fully cleared, marks existing open events as cleared.
- `tryInsertEvent` — Persists the new `AlertEvent` and publishes an `AlertFiredEvent` to the Spring application context. Catches `DataIntegrityViolationException` silently — the partial unique index on `(rule_id, endpoint_id) WHERE acked_at IS NULL` rejects a duplicate insert, which is the normal path when the evaluator fires again before the technician acks.

**Metric queries**
[`MetricQueryGateway.java`](../src/main/java/dev/pulsermm/alert/infrastructure/MetricQueryGateway.java):
- `conditionHolds` — Runs a native SQL `COUNT(*) FILTER (WHERE value OP :threshold)` against `public.metric_samples` scoped to the rolling window. Returns true only when every sample in the window breaches the threshold (`breaches == total AND total > 0`). Sparse data (no samples) does not trigger.
- `conditionCleared` — Same query; returns true when zero samples in the window breach the threshold (`breaches == 0 AND total > 0`). Used to unlock a rule for re-firing after the condition resolves.

**Endpoint resolution**
[`EndpointResolver.java`](../src/main/java/dev/pulsermm/alert/infrastructure/EndpointResolver.java):
- `resolve` — Given a `targetType` and `targetValue`, returns the list of endpoint UUIDs that the rule applies to. For `group` targets it queries `enrolment.endpoints` by `group_id`; for `tag` targets it splits the `key=value` string and joins `enrolment.endpoints` with `enrolment.endpoint_tags`.

**Domain event**
[`AlertFiredEvent.java`](../src/main/java/dev/pulsermm/alert/application/AlertFiredEvent.java):
- A plain Spring `ApplicationEvent` wrapping the persisted `AlertEvent`. Published by `tryInsertEvent` and consumed by `SseBroadcaster` in the same JVM process.

### description

The evaluator intentionally avoids any external messaging infrastructure this sprint. Because the SSE delivery is in-process, using Spring's `ApplicationEventPublisher` keeps the delivery path simple: the evaluator saves the row, publishes the event, and the broadcaster immediately pushes it to all connected `SseEmitter` instances. The only external dependency is the shared PostgreSQL database, where `metric_samples` lives in the `public` schema (owned by metric-service) and the alert tables live in the `alerts` schema (owned by this service).

Deduplication is enforced at the database level. The partial unique index `UNIQUE (rule_id, endpoint_id) WHERE acked_at IS NULL` means the evaluator can safely attempt an unconditional INSERT on every tick; Postgres rejects the duplicate and the `DataIntegrityViolationException` is swallowed. No application-level locking is needed.

A rule cannot re-fire for the same endpoint until two things are both true: the previous event has been acknowledged (`acked_at IS NOT NULL`) and the condition has since cleared (`cleared_at IS NOT NULL`). The `existsAckedButNotCleared` repository check enforces the second gate, preventing a rule that fires, gets acked, but remains breaching from immediately firing again.
