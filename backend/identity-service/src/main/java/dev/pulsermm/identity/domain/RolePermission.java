package dev.pulsermm.identity.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "role_permissions")
public class RolePermission {

    @EmbeddedId
    private RolePermissionId id;

    @Column(name = "group_scope_id")
    private UUID groupScopeId;

    public RolePermission() {}

    public RolePermission(UUID roleId, UUID permissionId) {
        this.id = new RolePermissionId(roleId, permissionId);
    }

    public RolePermission(UUID roleId, UUID permissionId, UUID groupScopeId) {
        this.id = new RolePermissionId(roleId, permissionId);
        this.groupScopeId = groupScopeId;
    }

    public RolePermissionId getId() { return id; }
    public UUID getGroupScopeId() { return groupScopeId; }
}
