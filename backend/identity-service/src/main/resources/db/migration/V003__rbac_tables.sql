CREATE TABLE permissions (
    id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE roles (
    id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE
);

-- group_scope_id is a bare UUID — no FK, references endpoint_groups which may live in another schema
CREATE TABLE role_permissions (
    role_id        UUID REFERENCES roles(id) ON DELETE CASCADE,
    permission_id  UUID REFERENCES permissions(id) ON DELETE CASCADE,
    group_scope_id UUID,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_roles (
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE user_permissions (
    user_id        UUID REFERENCES users(id) ON DELETE CASCADE,
    permission_id  UUID REFERENCES permissions(id) ON DELETE CASCADE,
    group_scope_id UUID,
    expires_at     TIMESTAMPTZ,
    PRIMARY KEY (user_id, permission_id)
);

CREATE TABLE endpoint_groups (
    id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE endpoint_group_memberships (
    endpoint_id UUID PRIMARY KEY,
    group_id    UUID REFERENCES endpoint_groups(id) ON DELETE SET NULL
);
