-- Permissions catalog
INSERT INTO permissions (name) VALUES
    ('endpoint:view'),
    ('endpoint:act'),
    ('endpoint:wipe'),
    ('endpoint:wipe:approve'),
    ('endpoint:structure:manage'),
    ('remote:shell'),
    ('remote:desktop:view'),
    ('remote:desktop:control'),
    ('remote:file'),
    ('remote:unattended'),
    ('script:run'),
    ('script:adhoc'),
    ('script:approve'),
    ('script:secret'),
    ('software:view'),
    ('software:manage'),
    ('policy:view'),
    ('policy:manage'),
    ('dashboard:view'),
    ('dashboard:manage'),
    ('alert:manage'),
    ('enrolment:manage'),
    ('identity:user:manage'),
    ('identity:rbac:manage'),
    ('identity:sso:manage'),
    ('audit:view'),
    ('audit:export'),
    ('integration:manage'),
    ('agent:manage'),
    ('platform:manage');

-- Default roles
INSERT INTO roles (name) VALUES
    ('Admin'),
    ('Senior Technician'),
    ('Junior Technician'),
    ('Auditor');

-- Admin gets all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'Admin';

-- Senior Technician
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'Senior Technician'
  AND p.name IN (
    'endpoint:view', 'endpoint:act', 'endpoint:wipe', 'endpoint:structure:manage',
    'remote:shell', 'remote:desktop:view', 'remote:desktop:control', 'remote:file', 'remote:unattended',
    'script:run', 'script:adhoc', 'script:secret',
    'software:view', 'software:manage',
    'policy:view',
    'dashboard:view', 'dashboard:manage',
    'alert:manage',
    'enrolment:manage',
    'audit:view'
  );

-- Junior Technician
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'Junior Technician'
  AND p.name IN (
    'endpoint:view',
    'remote:desktop:view',
    'script:run',
    'software:view',
    'policy:view',
    'dashboard:view',
    'alert:manage'
  );

-- Auditor
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'Auditor'
  AND p.name IN (
    'audit:view', 'audit:export',
    'endpoint:view',
    'software:view',
    'policy:view',
    'dashboard:view'
  );

-- Bootstrap admin: the Keycloak realm export ships an 'admin' user with this fixed id.
-- Keep these two in sync (deploy/keycloak-realm-export.json).
INSERT INTO user_roles (user_id, role_id)
SELECT '11111111-1111-1111-1111-111111111111'::uuid, r.id FROM roles r WHERE r.name = 'Admin';
