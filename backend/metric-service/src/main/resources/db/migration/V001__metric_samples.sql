CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

CREATE TABLE metric_samples (
    endpoint_id UUID        NOT NULL,
    type        VARCHAR(64) NOT NULL,
    value       DOUBLE PRECISION NOT NULL,
    sampled_at  TIMESTAMPTZ NOT NULL
);

SELECT create_hypertable('metric_samples', 'sampled_at');

CREATE INDEX ON metric_samples (endpoint_id, type, sampled_at DESC);

ALTER TABLE metric_samples SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'endpoint_id,type'
);

SELECT add_compression_policy('metric_samples', INTERVAL '7 days');

SELECT add_retention_policy('metric_samples', INTERVAL '30 days');
