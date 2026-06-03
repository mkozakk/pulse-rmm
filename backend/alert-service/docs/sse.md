# SSE Push Stream (`application/`, `api/controller/`)

Delivers new alert events to connected browsers in real-time over Server-Sent Events. When the evaluator fires an alert, it is immediately pushed to every open `EventSource` connection without any polling. A heartbeat comment is sent every 15 seconds to keep connections alive through proxies.

### code

**Broadcaster**
[`SseBroadcaster.java`](../src/main/java/dev/pulsermm/alert/application/SseBroadcaster.java):
- `register` - Creates a new `SseEmitter` with no timeout, adds it to the thread-safe `CopyOnWriteArrayList`, and attaches cleanup callbacks for completion, timeout, and error events so stale emitters are removed automatically.
- `onAlertFired` - Annotated with `@EventListener`; invoked synchronously by Spring when `AlertEvaluator` publishes an `AlertFiredEvent`. Serializes the `AlertEventResponse` to JSON and sends it as `event: alert\ndata: {...}` to every registered emitter. Emitters that throw on send are collected and removed after the loop.
- `heartbeat` - Scheduled every 15 seconds via `@Scheduled(fixedDelay = 15_000)`. Sends an SSE comment line (`": heartbeat"`) to all emitters to prevent proxy and load balancer timeouts from closing idle connections.

**Stream endpoint**
[`AlertEventController.java`](../src/main/java/dev/pulsermm/alert/api/controller/AlertEventController.java):
- `stream` - `GET /api/alerts/stream`, produces `text/event-stream`. Sets `X-Accel-Buffering: no` and `Cache-Control: no-cache` on the response (prevents nginx and CDN buffering from breaking the stream), then delegates to `SseBroadcaster.register()` and returns the emitter directly to Spring MVC.

**Token resolution for EventSource**
[`SseTokenFilter.java`](../src/main/java/dev/pulsermm/alert/infrastructure/config/SseTokenFilter.java):
- `doFilterInternal` - Runs once per request. If the request has a `token` query parameter and no existing `Authorization` header, wraps the request with a custom `HttpServletRequestWrapper` that injects the token as `Authorization: Bearer <token>`. This allows the browser `EventSource` API (which cannot set custom headers) to pass the JWT as `?token=<jwt>` on the stream URL. The wrapped request is then passed to Spring Security for validation.
- `BearerTokenRequest` - Inner class that intercepts calls to `getHeader("Authorization")` and `getHeaders("Authorization")` to return the injected bearer token. Spring Security reads the header and validates the JWT normally. Requests without a token or with an existing `Authorization` header bypass the wrapping.

**RabbitMQ consumer for domain events**
[`DomainEventConsumer.java`](../src/main/java/dev/pulsermm/alert/infrastructure/messaging/DomainEventConsumer.java):
- `onEvent` - Annotated with `@RabbitListener(queues = "alert.service.notifications")`. Consumes domain events from the RabbitMQ broker (published by other services). Converts the event type and data to a human-readable notification message (e.g., "Endpoint enrolled: server-01" from `event.type() = "endpoint.enrolled"`). Broadcasts the notification to all connected SSE clients via `SseBroadcaster`.
- `toMessage` - Translates domain event types (`endpoint.enrolled`, `endpoint.online`, `endpoint.offline`, `script.result`, `software.command.completed`, `audit.*`) into human-readable notifications. Extracts relevant data fields (hostname, endpointId, exitCode, action) from the event's data map. Audit events use the action suffix as the message.

**RabbitMQ queue setup**
[`NotificationRabbitConfig.java`](../src/main/java/dev/pulsermm/alert/infrastructure/config/NotificationRabbitConfig.java):
- `notificationQueue` - Declares a durable queue named `alert.service.notifications` on the RabbitMQ broker. This queue receives domain events from the `pulseEventsExchange` topic exchange.
- Binding beans - Six binding definitions that route specific event types from the shared `pulseEventsExchange` to the notification queue:
  - `endpoint.enrolled` - New endpoint enrollment
  - `endpoint.online` - Endpoint came online
  - `endpoint.offline` - Endpoint went offline
  - `audit.#` - All audit events (matches `audit.*` with wildcard)
  - `script.result` - Script execution result
  - `software.command.completed` - Software install/update/uninstall completion
  These bindings allow alert-service to receive notifications of fleet events without direct service-to-service calls.

### description

Server-Sent Events were chosen over WebSocket for alert delivery because notifications are strictly unidirectional (server → browser). `EventSource` reconnects automatically on network interruption, requires no protocol upgrade negotiation, and passes through corporate HTTP proxies more reliably than WebSocket.

The broadcaster is a singleton Spring component. All registered emitters are held in a `CopyOnWriteArrayList`, which avoids iterator-invalidation issues when the `onAlertFired` and `heartbeat` methods remove dead emitters mid-loop. Because the evaluator runs on a virtual thread and the broadcaster calls are short, no additional concurrency control is needed.

The token filter runs before Spring Security and converts the query parameter token into a standard `Authorization` header. This separation of concerns keeps the SSE stream endpoint clean - it does not need to know about alternative token locations.

The domain event consumer implements two-way notification: alerts are pushed when the evaluator fires (via in-process `ApplicationEvent`), and operational events (endpoint online/offline, script results, software actions) are pushed from other services via RabbitMQ. This keeps users informed of both alert state changes and fleet activity.
