package dev.pulsermm.alert.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAlertRuleRequest(
    @NotBlank @Size(max = 120)
    String name,

    @NotBlank @Pattern(regexp = "cpu|ram|disk", message = "must be cpu, ram, or disk")
    String metricType,

    @NotBlank @Pattern(regexp = ">|<", message = "must be > or <")
    String operator,

    @DecimalMin("0.0") @DecimalMax("100.0")
    double threshold,

    @Min(30) @Max(3600)
    int durationSecs,

    @NotNull @Valid
    TargetSpec target
) {
    public record TargetSpec(
        @NotBlank @Pattern(regexp = "group|tag", message = "must be group or tag")
        String type,

        @NotBlank @Size(max = 200)
        String value
    ) {}
}
