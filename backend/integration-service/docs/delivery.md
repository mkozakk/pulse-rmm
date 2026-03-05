# Delivery History (`api/`, `infrastructure/persistence/`)

Exposes delivery records so operators can audit what was sent, inspect failures, and review the dead-letter queue.

### code

**API**
[`WebhookDeliveryController.java`](../src/main/java/dev/pulsermm/integration/api/controller/WebhookDeliveryController.java):
	- `listByWebhook` - `GET /api/webhooks/{webhookId}/deliveries?status=&limit=50` — returns deliveries for one webhook, newest first. Optional `status` filter narrows to `success`, `retrying`, `dead_letter`, or `pending`. Capped at 200 rows.
	- `deadLetter` - `GET /api/deliveries/dead-letter?limit=100` — global view across all webhooks of deliveries in `dead_letter` state. Capped at 500 rows.
	- `getOne` - `GET /api/deliveries/{deliveryId}` — single delivery with the full payload JSON rather than the truncated preview.
[`WebhookDeliveryView.java`](../src/main/java/dev/pulsermm/integration/api/dto/WebhookDeliveryView.java):
	- `WebhookDeliveryView` - List-item projection: id, webhookId, eventType, eventId, payloadPreview (truncated to 200 chars), status, attempts, lastStatusCode, lastError, createdAt, completedAt.
[`WebhookDeliveryResponse.java`](../src/main/java/dev/pulsermm/integration/api/dto/WebhookDeliveryResponse.java):
	- `WebhookDeliveryResponse` - Detail projection: same fields as `WebhookDeliveryView` plus the full `payload` object (not truncated).

**Infrastructure**
[`WebhookDeliveryRepository.java`](../src/main/java/dev/pulsermm/integration/infrastructure/persistence/WebhookDeliveryRepository.java):
	- `findByWebhookIdOrderByCreatedAtDesc` - Fetches deliveries for one webhook, paginated by the `limit` param.
	- `findByStatusOrderByCreatedAtDesc` - Used by the dead-letter endpoint to fetch all `dead_letter` rows globally.
	- `findRetryable` - `JOIN FETCH`es the webhook association and filters `status IN ('pending', 'retrying') AND (next_retry_at IS NULL OR next_retry_at <= :now)`. Used by the retry scheduler.
	- `findByIdWithWebhook` - `JOIN FETCH`es the webhook so the controller can access `webhookId` without a separate query.

### description

The delivery list endpoints exist primarily for operators to diagnose failing webhooks. The `listByWebhook` endpoint is what the webapp's `WebhookDetailPage` polls every 10 s: it shows live status as deliveries move from `pending` → `retrying` → `success` or `dead_letter`. The `payloadPreview` field in the list response is capped at 200 characters; clicking a row fetches `getOne` which returns the full payload for inspection.

The dead-letter endpoint is a global cross-webhook view. If a URL has been unreachable for an extended period, multiple deliveries from different webhooks will accumulate here. The webapp surfaces this under `/webhooks` and also links to it from the individual webhook detail page.

### API reference

```
GET  /api/webhooks/{webhookId}/deliveries          List deliveries for one webhook
     ?status=success|retrying|dead_letter|pending  (optional) filter by status
     &limit=50                                      (optional, default 50, max 200)

GET  /api/deliveries/dead-letter                   All dead-letter deliveries
     ?limit=100                                     (optional, default 100, max 500)

GET  /api/deliveries/{deliveryId}                  Single delivery with full payload
```

**List item response shape:**

```json
{
  "id": "d2e1a3b4-...",
  "webhookId": "wh-uuid-...",
  "eventType": "alert.fired",
  "eventId": "evt-uuid-...",
  "payloadPreview": "{\"id\":\"...\",\"type\":\"alert.fired\",\"occurred_at\":\"...\",\"data\":{...}}",
  "status": "dead_letter",
  "attempts": 3,
  "lastStatusCode": 500,
  "lastError": null,
  "createdAt": "2026-05-15T12:00:00Z",
  "completedAt": "2026-05-15T12:00:09Z"
}
```

**Detail response** — same fields, plus `payload` as a full JSON object instead of `payloadPreview`.
