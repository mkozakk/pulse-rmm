package dev.pulsermm.metric.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "endpoint_heartbeats")
public class EndpointHeartbeat {

    @Id
    @Column(name = "endpoint_id")
    private UUID endpointId;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Column(nullable = false, length = 10)
    private String status;

    public EndpointHeartbeat() {}

    public EndpointHeartbeat(UUID endpointId, Instant lastSeen, String status) {
        this.endpointId = endpointId;
        this.lastSeen = lastSeen;
        this.status = status;
    }

    public UUID getEndpointId() { return endpointId; }
    public Instant getLastSeen() { return lastSeen; }
    public String getStatus() { return status; }

    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
    public void setStatus(String status) { this.status = status; }
}
