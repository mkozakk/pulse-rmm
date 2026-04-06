ALTER TABLE alert_rules ADD COLUMN org_id UUID;

CREATE INDEX idx_alert_rules_org_id ON alert_rules (org_id) WHERE org_id IS NOT NULL;
