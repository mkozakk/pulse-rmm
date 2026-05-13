# SSE Push Stream (`application/`, `api/controller/`)

Delivers new alert events to connected browsers in real-time over Server-Sent Events. When the evaluator fires an alert, it is immediately pushed to every open `EventSource` connection without any polling. A heartbeat comment is sent every 15 seconds to keep connections alive through proxies.

### code

**Broadcaster**
[`SseBroadcaster.java`](../src/main/java/dev/pulsermm/alert/application/SseBroadcaster.java):
- `register` — Creates a new `SseEmitter` with no timeout, adds it to the thread-safe `CopyOnWriteArrayList`, and attaches cleanup callbacks for completion, timeout, and error events so stale emitters are removed automatically.
- `onAlertFired` — Annotated with `@EventListener`; invoked synchronously by Spring when `AlertEvaluator` publishes an `AlertFiredEvent`. Serializes the `AlertEventResponse` to JSON and sends it as `event: alert\ndata: {...}` to every registered emitter. Emitters that throw on send are collected and removed after the loop.
- `heartbeat` — Scheduled every 15 seconds via `@Scheduled(fixedDelay = 15_000)`. Sends an SSE comment line (`": heartbeat"`) to all emitters to prevent proxy and load balancer timeouts from closing idle connections.

**Stream endpoint**
[`AlertEventController.java`](../src/main/java/dev/pulsermm/alert/api/controller/AlertEventController.java):
- `stream` — `GET /api/alerts/stream`, produces `text/event-stream`. Sets `X-Accel-Buffering: no` and `Cache-Control: no-cache` on the response (prevents nginx and CDN buffering from breaking the stream), then delegates to `SseBroadcaster.register()` and returns the emitter directly to Spring MVC.

**Token resolution for EventSource**
[`JwtAuthFilter.java`](../src/main/java/dev/pulsermm/alert/api/JwtAuthFilter.java):
- `resolveToken` — In addition to the standard `Authorization: Bearer` header, also reads the `token` query parameter. This is necessary because the browser `EventSource` API cannot set custom headers; the webapp passes the access token as `?token=<jwt>` on the stream URL. The same pattern is used by the API gateway for WebSocket upgrades.

### description

Server-Sent Events were chosen over WebSocket for alert delivery because notifications are strictly unidirectional (server → browser). `EventSource` reconnects automatically on network interruption, requires no protocol upgrade negotiation, and passes through corporate HTTP proxies more reliably than WebSocket.

The broadcaster is a singleton Spring component. All registered emitters are held in a `CopyOnWriteArrayList`, which avoids iterator-invalidation issues when the `onAlertFired` and `heartbeat` methods remove dead emitters mid-loop. Because the evaluator runs on a virtual thread and the broadcaster calls are short, no additional concurrency control is needed.

This sprint keeps the delivery in-process (same JVM, Spring `ApplicationEvent`). When webhooks are introduced in Sprint 16, the `AlertFiredEvent` publisher call in `AlertEvaluator` will be replaced with a `RabbitTemplate` publish, and a separate notification-service will own fan-out to external channels. The `SseBroadcaster` stays but consumes from the queue instead of the in-process event bus.
