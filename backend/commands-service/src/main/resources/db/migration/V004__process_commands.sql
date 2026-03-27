CREATE TABLE scripts.process_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    processes JSONB,
    error TEXT,
    requested_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE scripts.process_kill_commands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id UUID NOT NULL,
    pid INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    error TEXT,
    requested_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_process_snapshots_endpoint_created
    ON scripts.process_snapshots(endpoint_id, created_at DESC);
