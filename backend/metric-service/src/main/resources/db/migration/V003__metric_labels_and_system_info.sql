-- TimescaleDB 2.14 disallows DEFAULT with a cast expression on a compressed hypertable,
-- so the column is nullable and writers always supply a value.
ALTER TABLE metric_samples ADD COLUMN labels JSONB;
CREATE INDEX ON metric_samples USING GIN (labels);

CREATE TABLE endpoint_system_info (
    endpoint_id   UUID PRIMARY KEY,
    cpu_model     TEXT,
    cpu_physical  INT,
    cpu_logical   INT,
    cpu_freq_mhz  DOUBLE PRECISION,
    ram_total     BIGINT,
    swap_total    BIGINT,
    disks         JSONB NOT NULL DEFAULT '[]'::jsonb,
    nics          JSONB NOT NULL DEFAULT '[]'::jsonb,
    collected_at  TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
