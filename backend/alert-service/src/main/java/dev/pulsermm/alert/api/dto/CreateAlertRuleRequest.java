package dev.pulsermm.alert.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for creating an alert rule")
public record CreateAlertRuleRequest(
    @Schema(description = "Human-readable rule name", example = "High CPU on prod servers")
    @NotBlank @Size(max = 120)
    String name,

    @Schema(description = "Metric to evaluate", allowableValues = {"cpu", "ram", "disk"})
    @NotBlank @Pattern(regexp = "cpu|ram|disk", message = "must be cpu, ram, or disk")
    String metricType,

    @Schema(description = "Comparison operator", allowableValues = {">", "<"})
    @NotBlank @Pattern(regexp = ">|<", message = "must be > or <")
    String operator,

    @Schema(description = "Threshold value (0–100)", example = "90.0")
    @DecimalMin("0.0") @DecimalMax("100.0")
    double threshold,

    @Schema(description = "How long the condition must hold before firing, in seconds (30–3600)", example = "300")
    @Min(30) @Max(3600)
    int durationSecs,

    @Schema(description = "Scope the rule applies to")
    @NotNull @Valid
    TargetSpec target
) {
    @Schema(description = "Target scope for the alert rule")
    public record TargetSpec(
        @Schema(description = "Scope type", allowableValues = {"group", "tag"})
        @NotBlank @Pattern(regexp = "group|tag", message = "must be group or tag")
        String type,

        @Schema(description = "Group name or tag value")
        @NotBlank @Size(max = 200)
        String value
    ) {}
}
