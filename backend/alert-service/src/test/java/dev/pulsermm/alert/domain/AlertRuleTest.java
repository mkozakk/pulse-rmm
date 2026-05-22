package dev.pulsermm.alert.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleTest {

    @Test
    void creationSetsDefaultsCorrectly() {
        UUID userId = UUID.randomUUID();
        var rule = new AlertRule("High CPU", "cpu", ">", 80.0, 300, "group", "prod", userId);

        assertThat(rule.getName()).isEqualTo("High CPU");
        assertThat(rule.getMetricType()).isEqualTo("cpu");
        assertThat(rule.getOperator()).isEqualTo(">");
        assertThat(rule.getThreshold()).isEqualTo(80.0);
        assertThat(rule.getDurationSecs()).isEqualTo(300);
        assertThat(rule.getTargetType()).isEqualTo("group");
        assertThat(rule.getTargetValue()).isEqualTo("prod");
        assertThat(rule.isEnabled()).isTrue();
        assertThat(rule.getCreatedBy()).isEqualTo(userId);
        assertThat(rule.getCreatedAt()).isNotNull();
    }

    @Test
    void canBeDisabled() {
        var rule = new AlertRule("Test", "cpu", ">", 90.0, 60, "endpoint", "all", UUID.randomUUID());
        rule.setEnabled(false);

        assertThat(rule.isEnabled()).isFalse();
    }

    @Test
    void canHaveLongName() {
        var longName = "a".repeat(120);
        var rule = new AlertRule(longName, "mem", "<", 20.0, 120, "group", "test", UUID.randomUUID());

        assertThat(rule.getName().length()).isEqualTo(120);
    }

    @Test
    void differentThresholdTypes() {
        var rule1 = new AlertRule("Test1", "cpu", ">", 75.5, 300, "group", "prod", UUID.randomUUID());
        var rule2 = new AlertRule("Test2", "mem", "<", 10.0, 600, "endpoint", "single", UUID.randomUUID());

        assertThat(rule1.getThreshold()).isEqualTo(75.5);
        assertThat(rule2.getThreshold()).isEqualTo(10.0);
    }
}
