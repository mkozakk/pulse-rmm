-- V002 seeded a hardcoded admin UUID tied to the old realm export.
-- keycloak-user-init now creates the admin user dynamically and assigns the role.
DELETE FROM user_roles WHERE user_id = '11111111-1111-1111-1111-111111111111';
