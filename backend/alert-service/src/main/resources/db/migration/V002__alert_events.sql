CREATE TABLE alert_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id       UUID NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
    endpoint_id   UUID NOT NULL,
    triggered_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    acked_at      TIMESTAMPTZ,
    acked_by      UUID,
    cleared_at    TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_alert_events_open
    ON alert_events(rule_id, endpoint_id)
    WHERE acked_at IS NULL;

CREATE INDEX idx_alert_events_recent
    ON alert_events(triggered_at DESC);
