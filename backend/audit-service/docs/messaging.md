# Messaging & Persistence (`infrastructure/config/`, `infrastructure/messaging/`)

Receives audit events published by instrumented services over RabbitMQ and durably persists them to PostgreSQL.

### code
**RabbitMQ Infrastructure**
[`RabbitConfig.java`](../src/main/java/dev/pulsermm/audit/infrastructure/config/RabbitConfig.java):
	- Declares the durable `audit.events.persist` queue, the `audit.events` fanout exchange, and the binding between them. Declaring these resources here guarantees they exist before the consumer starts, regardless of which service connected to the broker first.

**Consumer**
[`AuditEventConsumer.java`](../src/main/java/dev/pulsermm/audit/infrastructure/messaging/AuditEventConsumer.java):
	- `consume` - Listens on `audit.events.persist` using `@RabbitListener`. Deserialises the incoming `AuditEventMessage` (Java serialisation via `SimpleMessageConverter`) and writes a new `AuditEvent` row. If the repository throws, the exception propagates and Spring AMQP redelivers the message, providing at-least-once persistence.

**Domain Entity**
[`AuditEvent.java`](../src/main/java/dev/pulsermm/audit/domain/AuditEvent.java):
	- JPA entity mapped to `audit.audit_events`. The `payload` column uses `@JdbcTypeCode(SqlTypes.JSON)` so arbitrary JSON structures are stored as JSONB in PostgreSQL without requiring a fixed schema.

**Publisher (common module)**
[`AuditAutoConfiguration.java`](../../common/src/main/java/dev/pulsermm/common/audit/AuditAutoConfiguration.java):
	- Auto-configures `AuditPublisher` and `AuditAspect` in any service that has `spring-boot-starter-amqp` on the classpath. The fanout exchange is also declared here so publishers and the consumer arrive at the same topology.

[`AuditAspect.java`](../../common/src/main/java/dev/pulsermm/common/audit/AuditAspect.java):
	- `@Around` advice that intercepts methods annotated with `@Auditable`. Extracts the acting user from `SecurityContextHolder`, an optional endpoint UUID from a `@EndpointId`-annotated parameter, and a payload from the first non-primitive argument. Publishes only after the method returns successfully — failed calls are never recorded.

### description
Because auditing must not break the calling service, the entire publish path is fire-and-forget: `AuditPublisher` catches and logs any broker exception rather than letting it propagate. On the consumer side the contract is reversed — a failure to persist is treated as a hard error so the message stays in the queue and is redelivered. The fanout exchange topology means additional consumers (e.g. a future real-time stream) can bind a new queue without touching existing producers or the persisting consumer.
