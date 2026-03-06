package dev.pulsermm.remote.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "desktop_sessions", schema = "remote")
public class DesktopSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Column(nullable = false)
    private String status;

    @Column(name = "turn_username")
    private String turnUsername;

    @Column(name = "turn_credential")
    private String turnCredential;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    protected DesktopSession() {}

    public DesktopSession(UUID endpointId, UUID technicianId, String turnUsername, String turnCredential) {
        this.endpointId = endpointId;
        this.technicianId = technicianId;
        this.status = "pending";
        this.turnUsername = turnUsername;
        this.turnCredential = turnCredential;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getEndpointId() { return endpointId; }
    public UUID getTechnicianId() { return technicianId; }
    public String getStatus() { return status; }
    public String getTurnUsername() { return turnUsername; }
    public String getTurnCredential() { return turnCredential; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }

    public void markActive() {
        this.status = "active";
    }

    public void markEnded() {
        this.status = "ended";
        this.endedAt = OffsetDateTime.now();
    }
}
