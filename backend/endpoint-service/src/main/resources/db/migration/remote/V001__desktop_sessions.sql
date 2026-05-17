CREATE TABLE desktop_sessions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id   UUID NOT NULL,
    technician_id UUID NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'pending',
    turn_username  VARCHAR(255),
    turn_credential VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at      TIMESTAMPTZ
);

CREATE INDEX idx_desktop_sessions_endpoint ON desktop_sessions(endpoint_id, created_at);
CREATE INDEX idx_desktop_sessions_status ON desktop_sessions(status) WHERE ended_at IS NULL;
