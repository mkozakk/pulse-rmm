CREATE TABLE organizations (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Org Admin: per-organization administrator. Scoped to its own org by org_id on the user.
INSERT INTO roles (name) VALUES ('Org Admin');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'Org Admin'
  AND p.name IN (
    'identity:user:manage',
    'identity:rbac:manage',
    'endpoint:structure:manage',
    'enrolment:manage',
    'audit:view'
  );
