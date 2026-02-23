package dev.pulsermm.metric.application;

import dev.pulsermm.metric.domain.EndpointHeartbeat;
import dev.pulsermm.metric.infrastructure.EndpointHeartbeatRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MetricIngestionService {

    private final EndpointHeartbeatRepository heartbeatRepo;
    private final JdbcTemplate jdbc;

    public MetricIngestionService(EndpointHeartbeatRepository heartbeatRepo, JdbcTemplate jdbc) {
        this.heartbeatRepo = heartbeatRepo;
        this.jdbc = jdbc;
    }

    @Transactional
    public void heartbeat(UUID endpointId) {
        Instant now = Instant.now();
        heartbeatRepo.findById(endpointId).ifPresentOrElse(
            h -> {
                h.setLastSeen(now);
                h.setStatus("online");
            },
            () -> heartbeatRepo.save(new EndpointHeartbeat(endpointId, now, "online"))
        );
    }

    public void pushMetrics(UUID endpointId, List<MetricSampleInput> samples) {
        for (var s : samples) {
            jdbc.update(
                "INSERT INTO metric_samples (endpoint_id, type, value, sampled_at) VALUES (?, ?, ?, CAST(? AS TIMESTAMPTZ))",
                endpointId, s.type(), s.value(), s.sampledAt().toString()
            );
        }
    }

    public List<Map<String, Object>> queryMetrics(UUID endpointId, Instant from, Instant to, String type) {
        return jdbc.queryForList(
            "SELECT sampled_at, value FROM metric_samples " +
            "WHERE endpoint_id = ? AND type = ? AND sampled_at >= CAST(? AS TIMESTAMPTZ) AND sampled_at <= CAST(? AS TIMESTAMPTZ) " +
            "ORDER BY sampled_at ASC",
            endpointId, type, from.toString(), to.toString()
        );
    }

    public record MetricSampleInput(String type, double value, Instant sampledAt) {}
}
