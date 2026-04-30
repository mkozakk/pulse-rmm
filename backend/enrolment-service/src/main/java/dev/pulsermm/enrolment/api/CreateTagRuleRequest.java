package dev.pulsermm.enrolment.api;

public record CreateTagRuleRequest(
    String conditionField,
    String conditionValue,
    String tagKey,
    String tagValue
) {}
