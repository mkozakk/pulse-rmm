package dev.pulsermm.agentupdate.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_update_events", schema = "agent_update")
public class AgentUpdateEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "endpoint_id")
    private UUID endpointId;

    @Column(nullable = false, length = 40)
    private String version;

    @Column(nullable = false, length = 20)
    private String status;

    @Column
    private String reason;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt = Instant.now();

    public UUID getId() { return id; }
    public UUID getEndpointId() { return endpointId; }
    public String getVersion() { return version; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }

    public void setEndpointId(UUID endpointId) { this.endpointId = endpointId; }
    public void setVersion(String version) { this.version = version; }
    public void setStatus(String status) { this.status = status; }
    public void setReason(String reason) { this.reason = reason; }
}
