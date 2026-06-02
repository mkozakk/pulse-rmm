ALTER TABLE integration.webhooks ADD COLUMN org_id UUID;

CREATE INDEX idx_webhooks_org_id ON integration.webhooks (org_id) WHERE org_id IS NOT NULL;
