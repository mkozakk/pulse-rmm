# Integration Service (`backend/integration-service/`)

The Integration Service manages outbound webhooks - it lets external systems react to Pulse RMM events without polling the REST API. When a domain event fires (alert, enrolment, audit record), the service matches it against registered webhooks, POSTs a signed JSON payload to each matching URL, and retries failures with exponential backoff. Permanent failures land in a dead-letter queue an admin can inspect.

## Directory Structure

```text
integration-service/
├── docs/                        # Full documentation of service features
├── src/main/java/.../
│   ├── api/                     # REST controllers, DTOs, JWT filter, exception handler
│   ├── application/             # Business logic: dispatch, retry scheduler, HMAC signing, secret encryption
│   ├── domain/                  # JPA entities: Webhook, WebhookDelivery
│   └── infrastructure/          # DB repositories, RabbitMQ consumer, Spring config
├── src/test/.../                # E2E tests (see e2e/tests/test_webhook_delivery.py)
├── Dockerfile                   # Containerization definition
└── pom.xml                      # Maven dependencies
```

## Features & Internal Documentation

* **[Webhook Management](docs/webhooks.md)** - Covers registration of webhook endpoints, event type matching, secret encryption at rest, and the full CRUD API.
* **[Dispatch & Retry](docs/dispatch.md)** - Explains HMAC-SHA256 request signing, the retry policy with exponential backoff, and how exhausted deliveries reach the dead-letter queue.
* **[Delivery History](docs/delivery.md)** - Documents the delivery history endpoints: per-webhook listing, global dead-letter view, and single delivery detail.
