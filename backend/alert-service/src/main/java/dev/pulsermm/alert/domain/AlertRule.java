package dev.pulsermm.alert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "metric_type", nullable = false, length = 20)
    private String metricType;

    @Column(nullable = false, length = 2)
    private String operator;

    @Column(nullable = false)
    private double threshold;

    @Column(name = "duration_secs", nullable = false)
    private int durationSecs;

    @Column(name = "target_type", nullable = false, length = 10)
    private String targetType;

    @Column(name = "target_value", nullable = false, length = 200)
    private String targetValue;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public AlertRule() {
    }

    public AlertRule(String name, String metricType, String operator, double threshold,
                     int durationSecs, String targetType, String targetValue, UUID createdBy) {
        this.name = name;
        this.metricType = metricType;
        this.operator = operator;
        this.threshold = threshold;
        this.durationSecs = durationSecs;
        this.targetType = targetType;
        this.targetValue = targetValue;
        this.createdBy = createdBy;
        this.enabled = true;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getMetricType() { return metricType; }
    public String getOperator() { return operator; }
    public double getThreshold() { return threshold; }
    public int getDurationSecs() { return durationSecs; }
    public String getTargetType() { return targetType; }
    public String getTargetValue() { return targetValue; }
    public boolean isEnabled() { return enabled; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
