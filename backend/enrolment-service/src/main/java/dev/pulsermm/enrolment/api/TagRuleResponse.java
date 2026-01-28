package dev.pulsermm.enrolment.api;

import dev.pulsermm.enrolment.domain.TagRule;

import java.util.UUID;

public record TagRuleResponse(
    UUID id,
    String conditionField,
    String conditionValue,
    String tagKey,
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
