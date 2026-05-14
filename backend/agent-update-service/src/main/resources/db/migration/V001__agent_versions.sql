CREATE SCHEMA IF NOT EXISTS agent_update;

CREATE TABLE agent_update.agent_versions (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    version      VARCHAR(40) NOT NULL,
    os           VARCHAR(20) NOT NULL,
    arch         VARCHAR(20) NOT NULL,
    artifact_key VARCHAR(200) NOT NULL,
    sha256       VARCHAR(64) NOT NULL,
    size_bytes   BIGINT      NOT NULL,
    is_current   BOOLEAN     NOT NULL DEFAULT false,
    published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (version, os, arch)
);

CREATE UNIQUE INDEX idx_agent_versions_current
    ON agent_update.agent_versions (os, arch)
    WHERE is_current = true;

CREATE TABLE agent_update.agent_update_events (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id UUID        NOT NULL,
    version     VARCHAR(40) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    reason      TEXT,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
