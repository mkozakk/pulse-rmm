package dev.pulsermm.identity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_permissions")
public class UserPermission {

    @EmbeddedId
    private UserPermissionId id;

    @Column(name = "group_scope_id")
    private UUID groupScopeId;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    public UserPermission() {}

    public UserPermission(UUID userId, UUID permissionId) {
        this.id = new UserPermissionId(userId, permissionId);
    }

    public UserPermission(UUID userId, UUID permissionId, UUID groupScopeId, OffsetDateTime expiresAt) {
        this.id = new UserPermissionId(userId, permissionId);
        this.groupScopeId = groupScopeId;
        this.expiresAt = expiresAt;
    }

    public UserPermissionId getId() { return id; }
    public UUID getGroupScopeId() { return groupScopeId; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
}
