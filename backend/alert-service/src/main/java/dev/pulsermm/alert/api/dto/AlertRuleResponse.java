package dev.pulsermm.alert.api.dto;

import dev.pulsermm.alert.domain.AlertRule;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AlertRuleResponse(
    UUID id,
    String name,
    String metricType,
    String operator,
    double threshold,
    int durationSecs,
    String targetType,
    String targetValue,
    boolean enabled,
    OffsetDateTime createdAt
) {
    public static AlertRuleResponse from(AlertRule rule) {
        return new AlertRuleResponse(
            rule.getId(),
            rule.getName(),
            rule.getMetricType(),
            rule.getOperator(),
            rule.getThreshold(),
            rule.getDurationSecs(),
            rule.getTargetType(),
            rule.getTargetValue(),
            rule.isEnabled(),
            rule.getCreatedAt()
        );
    }
}
