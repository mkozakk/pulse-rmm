package dev.pulsermm.metric.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MetricControllerTest {

    @Test
    void testConvertsSqlTimestamp() {
        var point = convertSampleToPoint(
            Map.of("sampled_at", java.sql.Timestamp.from(Instant.now()), "value", 42.5)
        );

        assertThat(point.value()).isEqualTo(42.5);
        assertThat(point.sampledAt()).isNotNull();
    }

    @Test
    void testConvertsOffsetDateTime() {
        Instant now = Instant.now();
        var odt = java.time.OffsetDateTime.ofInstant(now, java.time.ZoneId.of("UTC"));

        var point = convertSampleToPoint(
            Map.of("sampled_at", odt, "value", 80.0)
        );

        assertThat(point.value()).isEqualTo(80.0);
        assertThat(point.sampledAt()).isEqualTo(now);
    }

    @Test
    void testConvertsStringTimestamp() {
        Instant now = Instant.now();
        var point = convertSampleToPoint(
            Map.of("sampled_at", now.toString(), "value", 50.0)
        );

        assertThat(point.value()).isEqualTo(50.0);
    }

    private MetricController.MetricPointResponse convertSampleToPoint(Map<String, Object> row) {
        Object sampledAt = row.get("sampled_at");
        Instant instant;
        if (sampledAt instanceof java.sql.Timestamp ts) {
            instant = ts.toInstant();
        } else if (sampledAt instanceof java.time.OffsetDateTime odt) {
            instant = odt.toInstant();
        } else {
            instant = Instant.parse(sampledAt.toString());
        }
        return new MetricController.MetricPointResponse(
            instant,
            ((Number) row.get("value")).doubleValue()
        );
    }
}
