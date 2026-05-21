package dev.pulsermm.alert.api.dto;

import dev.pulsermm.alert.domain.AlertRule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "An alert rule definition")
public record AlertRuleResponse(
    @Schema(description = "Rule ID") UUID id,
    @Schema(description = "Human-readable rule name") String name,
    @Schema(description = "Metric to evaluate: cpu, ram, or disk") String metricType,
    @Schema(description = "Comparison operator: > or <") String operator,
    @Schema(description = "Threshold value (0–100)") double threshold,
    @Schema(description = "How long the condition must hold in seconds before firing") int durationSecs,
    @Schema(description = "Target scope: group or tag") String targetType,
    @Schema(description = "Target scope value (group name or tag value)") String targetValue,
    @Schema(description = "Whether the rule is active") boolean enabled,
    @Schema(description = "When the rule was created") OffsetDateTime createdAt
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
