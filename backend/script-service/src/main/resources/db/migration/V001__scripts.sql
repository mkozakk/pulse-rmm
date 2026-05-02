CREATE TABLE scripts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(256) NOT NULL,
    body TEXT NOT NULL,
    approved_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (created_by) REFERENCES identity.users(id)
);

CREATE TABLE script_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    script_id UUID NOT NULL,
    initiated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (script_id) REFERENCES scripts(id),
    FOREIGN KEY (initiated_by) REFERENCES identity.users(id)
);

CREATE TABLE script_run_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL,
    endpoint_id UUID NOT NULL,
    exit_code INTEGER,
    output TEXT,
    executed_at TIMESTAMPTZ,
    acked_at TIMESTAMPTZ,
    FOREIGN KEY (run_id) REFERENCES script_runs(id),
    FOREIGN KEY (endpoint_id) REFERENCES enrolment.endpoints(id)
);

CREATE TABLE script_secrets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL,
    key VARCHAR(256) NOT NULL,
    encrypted_value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (run_id) REFERENCES script_runs(id)
);

CREATE INDEX idx_script_runs_script_created ON script_runs(script_id, created_at);
CREATE INDEX idx_script_run_results_run_endpoint ON script_run_results(run_id, endpoint_id);
CREATE INDEX idx_script_run_results_acked ON script_run_results(acked_at);
CREATE INDEX idx_script_secrets_run ON script_secrets(run_id);
