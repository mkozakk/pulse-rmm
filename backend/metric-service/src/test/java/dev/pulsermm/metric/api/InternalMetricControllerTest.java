package dev.pulsermm.metric.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalMetricControllerTest {

    @Test
    void testParseValidUuidString() {
        UUID id = UUID.randomUUID();
        UUID parsed = UUID.fromString(id.toString());

        assertThat(parsed).isEqualTo(id);
    }

    @Test
    void testParseInvalidUuidThrows() {
        assertThatThrownBy(() -> UUID.fromString("not-a-uuid"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testConvertEpochMillisToInstant() {
        long millis = System.currentTimeMillis();
        Instant instant = Instant.ofEpochMilli(millis);

        assertThat(instant.toEpochMilli()).isEqualTo(millis);
    }

    @Test
    void testSampleInputCreation() {
        var sample = new InternalMetricController.SampleInput("cpu", 42.5, System.currentTimeMillis(), null);

        assertThat(sample.type()).isEqualTo("cpu");
        assertThat(sample.value()).isEqualTo(42.5);
        assertThat(sample.collectedAt()).isGreaterThan(0);
        assertThat(sample.labels()).isEmpty();
    }

    @Test
    void testMetricRequestCreation() {
        UUID endpointId = UUID.randomUUID();
        var req = new InternalMetricController.MetricRequest(endpointId.toString(), java.util.List.of());

        assertThat(req.endpointId()).isEqualTo(endpointId.toString());
    }
}
