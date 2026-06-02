-- Multi-tenancy: groups belong to an organization.
-- Nullable: a NULL org_id means the group was created by a global admin (no tenant).
ALTER TABLE groups ADD COLUMN org_id UUID;

CREATE INDEX idx_groups_org_id ON groups (org_id);
