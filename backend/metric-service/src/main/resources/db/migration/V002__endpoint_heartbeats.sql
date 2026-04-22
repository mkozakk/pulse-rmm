CREATE TABLE endpoint_heartbeats (
    endpoint_id UUID        PRIMARY KEY,
    last_seen   TIMESTAMPTZ NOT NULL DEFAULT now(),
    status      VARCHAR(10) NOT NULL DEFAULT 'online'
);
