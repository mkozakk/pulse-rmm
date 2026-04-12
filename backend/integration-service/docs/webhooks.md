# Webhook Management (`api/`, `application/`, `domain/`, `infrastructure/`)

Handles registration of webhook endpoints, secret encryption, and event-type matching against incoming domain events.

### code

**Domain**
[`Webhook.java`](../src/main/java/dev/pulsermm/integration/domain/Webhook.java):
	- `Webhook` - JPA entity representing one registered webhook: url, encrypted secret, list of event type patterns, enabled flag, and the user who created it.

**Application**
[`WebhookService.java`](../src/main/java/dev/pulsermm/integration/application/WebhookService.java):
	- `create` - Validates the URL, encrypts the caller-supplied secret using AES-GCM, and persists the new webhook.
	- `update` - Updates url, event types, and enabled state; re-encrypts the secret only when a new one is supplied.
	- `delete` - Removes the webhook; the FK cascade on `webhook_deliveries` cleans up delivery rows automatically.
[`WebhookSecretEncryptor.java`](../src/main/java/dev/pulsermm/integration/application/WebhookSecretEncryptor.java):
	- `encrypt` - Generates a random 12-byte nonce, runs AES-GCM, and prepends the nonce to the ciphertext before storage.
	- `decrypt` - Splits the stored bytes into nonce + ciphertext and recovers the plaintext secret for signing.

**Infrastructure**
[`WebhookEncryptionConfig.java`](../src/main/java/dev/pulsermm/integration/infrastructure/config/WebhookEncryptionConfig.java):
	- `webhookSecretEncryptor` - Reads `WEBHOOK_SECRET_KEK` from the environment, validates it is at least 16 bytes, and constructs the encryptor bean.
[`WebhookRepository.java`](../src/main/java/dev/pulsermm/integration/infrastructure/persistence/WebhookRepository.java):
	- `findAllEnabled` - Returns all webhooks where `enabled = true`; used by the event consumer to find candidates for dispatch.
[`WebhookEventConsumer.java`](../src/main/java/dev/pulsermm/integration/infrastructure/messaging/WebhookEventConsumer.java):
	- `onEvent` - Receives a domain event from RabbitMQ, iterates enabled webhooks, and matches each webhook's event type patterns against the incoming event type. Supports exact matches (`alert.fired`) and wildcard prefix matches (`audit.*`). Creates a `WebhookDelivery` row and immediately triggers dispatch for each match.
[`RabbitConfig.java`](../src/main/java/dev/pulsermm/integration/infrastructure/config/RabbitConfig.java):
	- `integrationQueue` - Declares the durable RabbitMQ queue and binds it to the domain event topic exchange with a `#` wildcard routing key so the integration service receives every domain event published by other services.

**API**
[`WebhookController.java`](../src/main/java/dev/pulsermm/integration/api/controller/WebhookController.java):
	- `create` - `POST /api/webhooks` - accepts url, event_types[], and secret; returns 201 with the created webhook (secret never echoed).
	- `list` - `GET /api/webhooks` - returns all webhooks for the tenant, secrets excluded.
	- `update` - `PUT /api/webhooks/{id}` - partial update; secret field is optional.
	- `delete` - `DELETE /api/webhooks/{id}` - returns 204.

### description

When an admin registers a webhook, `WebhookController` delegates to `WebhookService`, which encrypts the caller-supplied secret before touching the database. Encryption uses AES-GCM inside `WebhookSecretEncryptor`: a fresh 12-byte nonce is generated per secret, the GCM cipher runs with the KEK loaded by `WebhookEncryptionConfig`, and the `[nonce || ciphertext]` byte array is stored in `webhooks.secret_ciphertext` alongside the `kek_id` string for future key rotation. The plaintext secret is never written anywhere.

When a domain event arrives over RabbitMQ, `WebhookEventConsumer.onEvent` fetches all enabled webhooks and tests each one's `event_types` array against the event's type string. Exact strings match directly; patterns ending with `.*` match any event whose type starts with the prefix (e.g. `audit.*` matches `audit.alert_rule.create`). For each matching webhook a `WebhookDelivery` row is created and handed to the dispatcher.

### event catalog

| event type | published by | fired when |
|---|---|---|
| `alert.fired` | alert-service | an alert rule threshold is breached |
| `alert.acknowledged` | alert-service | an operator acks an open alert |
| `endpoint.enrolled` | enrolment-service | a new agent completes enrolment |
| `endpoint.online` | metric-service | agent heartbeat resumes after absence |
| `endpoint.offline` | metric-service | agent heartbeat stops |
| `audit.*` | audit-service | any auditable action is recorded (wildcard) |

### payload envelope

Every webhook POST body uses the same envelope regardless of event type:

```json
{
  "id": "9f4e2a1b-...",
  "type": "alert.fired",
  "occurred_at": "2026-05-15T12:00:00Z",
  "data": {
    // event-specific fields
  }
}
```

The `data` object varies per event type. For `audit.*` events it contains the audit record fields (action, actor, resource, etc.).
