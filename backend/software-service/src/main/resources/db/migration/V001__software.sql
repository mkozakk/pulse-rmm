CREATE TABLE software_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id UUID NOT NULL,
    name VARCHAR(256) NOT NULL,
    version VARCHAR(128) NOT NULL,
    source VARCHAR(64) NOT NULL,
    last_scanned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(endpoint_id, name)
);

CREATE TABLE software_commands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    package_name VARCHAR(256) NOT NULL,
    package_version VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    exit_code INTEGER,
    output TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_software_items_endpoint ON software_items(endpoint_id);
CREATE INDEX idx_software_commands_endpoint ON software_commands(endpoint_id, status);
CREATE INDEX idx_software_commands_id ON software_commands(id);
