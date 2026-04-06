ALTER TABLE scripts.scripts ADD COLUMN org_id UUID;
ALTER TABLE scripts.scripts ADD COLUMN is_global BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_scripts_org_id ON scripts.scripts (org_id) WHERE org_id IS NOT NULL;
