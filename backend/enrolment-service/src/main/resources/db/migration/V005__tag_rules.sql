CREATE TABLE tag_rules (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    condition_field VARCHAR(64)  NOT NULL,
    condition_value VARCHAR(256) NOT NULL,
    tag_key         VARCHAR(64)  NOT NULL,
    tag_value       VARCHAR(256) NOT NULL
);
