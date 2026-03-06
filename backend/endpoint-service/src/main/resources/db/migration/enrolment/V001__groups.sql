CREATE TABLE groups (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name      VARCHAR(128) NOT NULL,
    parent_id UUID REFERENCES groups(id)
);
