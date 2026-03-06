package dev.pulsermm.enrolment.api.dto;

import dev.pulsermm.enrolment.domain.TagRule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record TagRuleResponse(
    @Schema(description = "Rule id")
    UUID id,
    @Schema(description = "Endpoint field to match", example = "hostname")
    String conditionField,
    @Schema(description = "Value to match", example = "DESKTOP")
    String conditionValue,
    @Schema(description = "Tag key", example = "env")
    String tagKey,
    @Schema(description = "Tag value", example = "prod")
    String tagValue
) {
    public static TagRuleResponse from(TagRule rule) {
        return new TagRuleResponse(
            rule.getId(),
            rule.getConditionField(),
            rule.getConditionValue(),
            rule.getTagKey(),
            rule.getTagValue()
        );
    }
}
