CREATE SCHEMA IF NOT EXISTS integration;

CREATE TABLE integration.webhooks (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url                 VARCHAR(500)    NOT NULL,
    secret_ciphertext   BYTEA           NOT NULL,
    secret_kek_id       VARCHAR(50)     NOT NULL,
    event_types         TEXT[]          NOT NULL,
    enabled             BOOLEAN         NOT NULL DEFAULT true,
    created_by          UUID            NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhooks_enabled ON integration.webhooks (enabled) WHERE enabled;

CREATE TABLE integration.webhook_deliveries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id      UUID            NOT NULL REFERENCES integration.webhooks(id) ON DELETE CASCADE,
    event_type      VARCHAR(80)     NOT NULL,
    event_id        UUID            NOT NULL,
    payload         JSONB           NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    attempts        INT             NOT NULL DEFAULT 0,
    last_status_code INT,
    last_error      TEXT,
    next_retry_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_deliveries_webhook ON integration.webhook_deliveries (webhook_id, created_at DESC);
CREATE INDEX idx_deliveries_retryable ON integration.webhook_deliveries (status, next_retry_at)
    WHERE status IN ('pending', 'retrying');
