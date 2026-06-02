ALTER TABLE audit_events ADD COLUMN org_id UUID;

CREATE INDEX idx_audit_org_id ON audit_events (org_id) WHERE org_id IS NOT NULL;
