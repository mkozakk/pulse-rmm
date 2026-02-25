package dev.pulsermm.audit.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events", schema = "audit")
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username")
    private String username;

    @Column(name = "permission_used", nullable = false)
    private String permissionUsed;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "endpoint_id")
    private UUID endpointId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEvent() {}

    public AuditEvent(UUID id, UUID userId, String username, String permissionUsed,
                      String action, UUID endpointId, String payload, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.permissionUsed = permissionUsed;
        this.action = action;
        this.endpointId = endpointId;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPermissionUsed() { return permissionUsed; }
    public String getAction() { return action; }
    public UUID getEndpointId() { return endpointId; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
