-- group_id is a bare UUID reference — same pattern as group_scope_id in role_permissions
ALTER TABLE endpoint_group_memberships DROP CONSTRAINT endpoint_group_memberships_group_id_fkey;
