# Alert Service (`backend/alert-service/`)

The Alert Service defines threshold-based alerting rules on time-series metrics collected by the metric service. A scheduled evaluator checks each enabled rule against TimescaleDB every 30 seconds; when the condition holds for the full configured duration, an alert event is persisted and broadcast live to connected browsers over Server-Sent Events. Technicians acknowledge alerts from the notification bell; a rule cannot re-fire until the condition has fully cleared after acknowledgement.

## Directory Structure

```text
alert-service/
├── docs/                   # Full documentation of alert-service features
├── src/main/java/.../
│   ├── api/                # REST controllers, DTOs, JWT auth filter, global error handler
│   ├── application/        # Alert rule service, alert event service, evaluator, SSE broadcaster
│   ├── domain/             # AlertRule and AlertEvent JPA entities
│   └── infrastructure/     # JPA repositories, MetricQueryGateway, EndpointResolver, SecurityConfig
├── src/main/resources/
│   └── db/migration/       # Flyway migrations (V001 rules, V002 events)
├── Dockerfile              # Containerization definition
└── pom.xml                 # Maven dependencies
```

## Features & Internal Documentation

* **[Alerting Evaluator](docs/evaluator.md)** — Covers the scheduled evaluation loop, TimescaleDB breach-count query, endpoint resolution by group or tag, deduplication via partial unique index, and the condition-clear logic that allows a rule to re-fire.
* **[SSE Push Stream](docs/sse.md)** — Explains how new alert events are broadcast to connected browsers in real-time using Spring `SseEmitter`, including the in-process event bridge and heartbeat scheduling.
* **[REST API](docs/api.md)** — Documents the rule CRUD endpoints, alert listing and acknowledgement endpoint, and the JWT security configuration applied to all `/api/**` paths.
