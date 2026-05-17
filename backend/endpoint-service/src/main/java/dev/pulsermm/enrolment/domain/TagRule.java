package dev.pulsermm.enrolment.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tag_rules", schema = "enrolment")
public class TagRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "condition_field", nullable = false, length = 64)
    private String conditionField;

    @Column(name = "condition_value", nullable = false, length = 256)
    private String conditionValue;

    @Column(name = "tag_key", nullable = false, length = 64)
    private String tagKey;

    @Column(name = "tag_value", nullable = false, length = 256)
    private String tagValue;

    public TagRule() {}

    public TagRule(String conditionField, String conditionValue, String tagKey, String tagValue) {
        this.conditionField = conditionField;
        this.conditionValue = conditionValue;
        this.tagKey = tagKey;
        this.tagValue = tagValue;
    }

    public UUID getId() {
        return id;
    }

    public String getConditionField() {
        return conditionField;
    }

    public String getConditionValue() {
        return conditionValue;
    }

    public String getTagKey() {
        return tagKey;
    }

    public String getTagValue() {
        return tagValue;
    }
}
