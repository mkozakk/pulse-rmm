CREATE TABLE audit_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID,
    username        VARCHAR(120),
    permission_used VARCHAR(80)  NOT NULL,
    action          VARCHAR(120) NOT NULL,
    endpoint_id     UUID,
    payload         JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_created_at  ON audit_events (created_at DESC);
CREATE INDEX idx_audit_user_id     ON audit_events (user_id);
CREATE INDEX idx_audit_endpoint_id ON audit_events (endpoint_id) WHERE endpoint_id IS NOT NULL;
