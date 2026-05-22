package dev.pulsermm.enrolment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "endpoint_revocations", schema = "enrolment")
public class EndpointRevocation {
    @Id
    @Column(name = "endpoint_id")
    private UUID endpointId;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    @Column(length = 255)
    private String reason;

    public EndpointRevocation() {}

    public EndpointRevocation(UUID endpointId, Instant revokedAt, String reason) {
        this.endpointId = endpointId;
        this.revokedAt = revokedAt;
        this.reason = reason;
    }

    public UUID getEndpointId() { return endpointId; }
    public void setEndpointId(UUID endpointId) { this.endpointId = endpointId; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
