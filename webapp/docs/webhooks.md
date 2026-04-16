# Webhooks (`src/pages/WebhooksPage.jsx`, `src/pages/WebhookDetailPage.jsx`)

Manages outbound webhook subscriptions and inspects their delivery history, including the dead-letter queue.

### code
[`pages/WebhooksPage.jsx`](../src/pages/WebhooksPage.jsx):
	- `WebhooksPage` - Lists subscriptions (`useListWebhooksQuery`) and creates, edits, enables/disables, or deletes them (`useCreateWebhookMutation`, `useUpdateWebhookMutation`, `useDeleteWebhookMutation`).
	- `EVENT_TYPE_OPTIONS` - The selectable event types a webhook can subscribe to (`alert.fired`, `endpoint.enrolled`, `endpoint.online/offline`, `audit.*`, …), plus a target URL and a signing secret.

[`pages/WebhookDetailPage.jsx`](../src/pages/WebhookDetailPage.jsx):
	- `WebhookDetailPage` - Shows one webhook's recent deliveries (`useListDeliveriesQuery`) and drills into a single attempt (`useGetDeliveryQuery`).
	- `StatusBadge` - Colour-codes each delivery as success, retrying, dead-letter, or pending.

### description
A webhook is a subscription: a URL, a set of event types, and a shared secret the backend uses to HMAC-sign each payload so the receiver can verify it. The list page is the management surface for those subscriptions; toggling `enabled` lets an operator pause deliveries without losing the configuration.

The detail page is the diagnostic surface. Outbound delivery is best-effort with retries and a dead-letter queue on the backend, so every attempt has a status — pending, retrying, success, or dead-letter — surfaced here per delivery and drillable down to the individual request/response. Delivery queries are uncached so the view reflects the true, current state of each attempt while debugging an integration.
</content>
