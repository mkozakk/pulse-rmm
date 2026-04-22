CREATE TABLE endpoints (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hostname    VARCHAR(255) NOT NULL,
    os          VARCHAR(64)  NOT NULL,
    arch        VARCHAR(32)  NOT NULL,
    group_id    UUID NOT NULL REFERENCES groups(id),
    public_key  BYTEA NOT NULL UNIQUE,
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen   TIMESTAMPTZ NOT NULL DEFAULT now()
);
