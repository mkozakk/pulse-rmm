package dev.pulsermm.alert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert_events")
public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "rule_id", nullable = false)
    private AlertRule rule;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "triggered_at", nullable = false)
    private OffsetDateTime triggeredAt;

    @Column(name = "acked_at")
    private OffsetDateTime ackedAt;

    @Column(name = "acked_by")
    private UUID ackedBy;

    @Column(name = "cleared_at")
    private OffsetDateTime clearedAt;

    public AlertEvent() {
    }

    public AlertEvent(AlertRule rule, UUID endpointId) {
        this.rule = rule;
        this.endpointId = endpointId;
        this.triggeredAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public AlertRule getRule() { return rule; }
    public UUID getEndpointId() { return endpointId; }
    public OffsetDateTime getTriggeredAt() { return triggeredAt; }
    public OffsetDateTime getAckedAt() { return ackedAt; }
    public UUID getAckedBy() { return ackedBy; }
    public OffsetDateTime getClearedAt() { return clearedAt; }

    public void ack(UUID userId) {
        this.ackedAt = OffsetDateTime.now();
        this.ackedBy = userId;
    }

    public void markCleared() {
        this.clearedAt = OffsetDateTime.now();
    }

    public boolean isOpen() {
        return ackedAt == null;
    }
}
