package dev.pulsermm.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class RolePermissionId implements Serializable {

    @Column(name = "role_id")
    private UUID roleId;

    @Column(name = "permission_id")
    private UUID permissionId;

    public RolePermissionId() {}

    public RolePermissionId(UUID roleId, UUID permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    public UUID getRoleId() { return roleId; }
    public UUID getPermissionId() { return permissionId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RolePermissionId that)) return false;
        return Objects.equals(roleId, that.roleId) && Objects.equals(permissionId, that.permissionId);
    }

    @Override
    public int hashCode() { return Objects.hash(roleId, permissionId); }
}
