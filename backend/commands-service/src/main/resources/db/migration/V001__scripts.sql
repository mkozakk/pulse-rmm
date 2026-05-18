CREATE SCHEMA IF NOT EXISTS scripts;

CREATE TABLE scripts.scripts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(256) NOT NULL,
    body TEXT NOT NULL,
    approved_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE scripts.script_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    script_id UUID NOT NULL REFERENCES scripts.scripts(id),
    initiated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE scripts.script_run_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES scripts.script_runs(id),
    endpoint_id UUID NOT NULL,
    exit_code INTEGER,
    output TEXT,
    executed_at TIMESTAMPTZ,
    acked_at TIMESTAMPTZ
);

CREATE TABLE scripts.script_secrets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES scripts.script_runs(id),
    key VARCHAR(256) NOT NULL,
    encrypted_value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_script_runs_script_created ON scripts.script_runs(script_id, created_at);
CREATE INDEX idx_script_run_results_run_endpoint ON scripts.script_run_results(run_id, endpoint_id);
CREATE INDEX idx_script_run_results_acked ON scripts.script_run_results(acked_at);
CREATE INDEX idx_script_secrets_run ON scripts.script_secrets(run_id);
