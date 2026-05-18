package dev.pulsermm.alert.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlertEventTest {

    @Test
    void creationSetsInitialState() {
        UUID ruleId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        var rule = new AlertRule("Test", "cpu", ">", 80.0, 300, "group", "prod", ruleId);
        var event = new AlertEvent(rule, endpointId);

        assertThat(event.getRule()).isEqualTo(rule);
        assertThat(event.getEndpointId()).isEqualTo(endpointId);
        assertThat(event.getTriggeredAt()).isNotNull();
        assertThat(event.isOpen()).isTrue();
        assertThat(event.getAckedAt()).isNull();
        assertThat(event.getAckedBy()).isNull();
        assertThat(event.getClearedAt()).isNull();
    }

    @Test
    void ackSetsTimestampAndUser() {
        var rule = new AlertRule("Test", "cpu", ">", 80.0, 300, "group", "prod", UUID.randomUUID());
        var event = new AlertEvent(rule, UUID.randomUUID());
        UUID userId = UUID.randomUUID();

        event.ack(userId);

        assertThat(event.getAckedAt()).isNotNull();
        assertThat(event.getAckedBy()).isEqualTo(userId);
        assertThat(event.isOpen()).isFalse();
    }

    @Test
    void clearSetsTimestamp() {
        var rule = new AlertRule("Test", "cpu", ">", 80.0, 300, "group", "prod", UUID.randomUUID());
        var event = new AlertEvent(rule, UUID.randomUUID());

        event.markCleared();

        assertThat(event.getClearedAt()).isNotNull();
    }

    @Test
    void eventCanBeAckedThenCleared() {
        var rule = new AlertRule("Test", "cpu", ">", 80.0, 300, "group", "prod", UUID.randomUUID());
        var event = new AlertEvent(rule, UUID.randomUUID());

        event.ack(UUID.randomUUID());
        event.markCleared();

        assertThat(event.getAckedAt()).isNotNull();
        assertThat(event.getClearedAt()).isNotNull();
    }
}
