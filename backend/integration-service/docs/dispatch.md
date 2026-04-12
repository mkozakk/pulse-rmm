# Dispatch & Retry (`application/`)

Signs outbound webhook requests with HMAC-SHA256 and retries failures with exponential backoff. Deliveries that exhaust all attempts are moved to a dead-letter state.

### code

**Application**
[`WebhookDispatcher.java`](../src/main/java/dev/pulsermm/integration/application/WebhookDispatcher.java):
	- `dispatch` - Entry point for a single delivery attempt. Increments the attempt counter, serialises the payload to JSON, decrypts the webhook secret, signs the body, and POSTs to the registered URL. Inspects the response status and delegates to success, retry, or dead-letter handling.
	- `markDead` - Sets `status = dead_letter` and `completed_at = now`. Called when a non-retryable status code is received or when attempts reach 3.
	- `backoffSeconds` - Computes the delay before the next attempt: `4^(attempts-1)` → 1 s after attempt 1, 4 s after attempt 2. No third delay - attempt 3 exhausts retries.
[`HmacSigner.java`](../src/main/java/dev/pulsermm/integration/application/HmacSigner.java):
	- `sign` - Runs `javax.crypto.Mac` with algorithm `HmacSHA256` over the raw request body bytes and the decrypted secret. Returns the hex digest prefixed with `sha256=`.
[`WebhookRetryScheduler.java`](../src/main/java/dev/pulsermm/integration/application/WebhookRetryScheduler.java):
	- `retryPending` - Scheduled every 5 s. Queries for deliveries in `pending` or `retrying` state whose `next_retry_at <= now` and calls `WebhookDispatcher.dispatch` for each.

**Infrastructure**
[`RestClientConfig.java`](../src/main/java/dev/pulsermm/integration/infrastructure/config/RestClientConfig.java):
	- `webhookRestClient` - Configures Spring 6 `RestClient` with 5 s connect timeout and 10 s read timeout via `SimpleClientHttpRequestFactory`. Shared by all dispatch calls.

### description

Every outbound POST carries four headers the receiver can use for verification and routing:

| header | example value | purpose |
|---|---|---|
| `X-Pulse-Signature` | `sha256=a3f8...` | HMAC-SHA256 of the raw body using the webhook secret |
| `X-Pulse-Event` | `alert.fired` | event type string |
| `X-Pulse-Delivery` | `9f4e2a1b-...` | delivery UUID - idempotency key for the receiver |
| `User-Agent` | `PulseRMM-Webhook/1.0` | identifies the sender |

**Signature verification** (receiver side):

```python
import hmac, hashlib

def verify(body_bytes, secret, header):
    expected = "sha256=" + hmac.new(secret.encode(), body_bytes, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, header)
```

```javascript
const crypto = require('crypto')
function verify(bodyBuffer, secret, header) {
  const expected = 'sha256=' + crypto.createHmac('sha256', secret).update(bodyBuffer).digest('hex')
  return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(header))
}
```

Always use a constant-time comparison to prevent timing attacks.

**Retry policy**:

| attempt | trigger | next action |
|---|---|---|
| 1 | non-2xx or timeout | wait 1 s, then attempt 2 |
| 2 | non-2xx or timeout | wait 4 s, then attempt 3 |
| 3 | non-2xx or timeout | dead_letter - no further attempts |

Retryable status codes: `5xx`, `408`, `429`, and connection / read timeout (`ResourceAccessException`).  
Non-retryable: `4xx` (except 408 and 429) - these go to dead_letter immediately after attempt 1, no backoff.

A `200–299` response on any attempt marks the delivery `success` and sets `completed_at`.
