package dev.pulsermm.rbac.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserPermissionId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "permission_id")
    private UUID permissionId;

    public UserPermissionId() {}

    public UserPermissionId(UUID userId, UUID permissionId) {
        this.userId = userId;
        this.permissionId = permissionId;
    }

    public UUID getUserId() { return userId; }
    public UUID getPermissionId() { return permissionId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserPermissionId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(permissionId, that.permissionId);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, permissionId); }
}
