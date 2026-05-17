package dev.pulsermm.enrolment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateTagRuleRequest(
    @Schema(description = "Endpoint field to match", example = "hostname")
    String conditionField,
    @Schema(description = "Value to match", example = "DESKTOP")
    String conditionValue,
    @Schema(description = "Tag key to set", example = "env")
    String tagKey,
    @Schema(description = "Tag value to set", example = "prod")
    String tagValue
) {}
