CREATE TABLE endpoint_tags (
    endpoint_id UUID        NOT NULL REFERENCES endpoints(id) ON DELETE CASCADE,
    key         VARCHAR(64)  NOT NULL,
    value       VARCHAR(256) NOT NULL,
    PRIMARY KEY (endpoint_id, key)
);
