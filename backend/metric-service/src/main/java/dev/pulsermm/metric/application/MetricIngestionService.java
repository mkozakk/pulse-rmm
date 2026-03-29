package dev.pulsermm.metric.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.common.events.DomainEvent;
import dev.pulsermm.common.events.DomainEventPublisher;
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
    private final DomainEventPublisher domainEventPublisher;
    private final ObjectMapper json = new ObjectMapper();

    public MetricIngestionService(EndpointHeartbeatRepository heartbeatRepo, JdbcTemplate jdbc,
                                   DomainEventPublisher domainEventPublisher) {
        this.heartbeatRepo = heartbeatRepo;
        this.jdbc = jdbc;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public void heartbeat(UUID endpointId) {
        Instant now = Instant.now();
        heartbeatRepo.findById(endpointId).ifPresentOrElse(
            h -> {
                boolean wasOffline = "offline".equals(h.getStatus());
                h.setLastSeen(now);
                h.setStatus("online");
                if (wasOffline) {
                    domainEventPublisher.publish(DomainEvent.of("endpoint.online",
                        Map.of("endpointId", endpointId.toString())));
                }
            },
            () -> heartbeatRepo.save(new EndpointHeartbeat(endpointId, now, "online"))
        );
    }

    public void pushMetrics(UUID endpointId, List<MetricSampleInput> samples) {
        for (var s : samples) {
            jdbc.update(
                "INSERT INTO metric_samples (endpoint_id, type, value, sampled_at, labels) " +
                "VALUES (?, ?, ?, CAST(? AS TIMESTAMPTZ), CAST(? AS JSONB))",
                endpointId, s.type(), s.value(), s.sampledAt().toString(), toJson(s.labels())
            );
        }
    }

    public List<Map<String, Object>> queryMetrics(UUID endpointId, Instant from, Instant to,
                                                   String type, Map<String, String> labelFilter) {
        StringBuilder sql = new StringBuilder(
            "SELECT sampled_at, value, labels FROM metric_samples " +
            "WHERE endpoint_id = ? AND type = ? " +
            "AND sampled_at >= CAST(? AS TIMESTAMPTZ) AND sampled_at <= CAST(? AS TIMESTAMPTZ)"
        );
        List<Object> args = new java.util.ArrayList<>(
            List.of(endpointId, type, from.toString(), to.toString())
        );
        if (labelFilter != null && !labelFilter.isEmpty()) {
            sql.append(" AND labels @> CAST(? AS JSONB)");
            args.add(toJson(labelFilter));
        }
        sql.append(" ORDER BY sampled_at ASC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public void upsertSystemInfo(SystemInfoInput info) {
        jdbc.update(
            "INSERT INTO endpoint_system_info (endpoint_id, cpu_model, cpu_physical, cpu_logical, " +
            "cpu_freq_mhz, ram_total, swap_total, disks, nics, collected_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), CAST(? AS TIMESTAMPTZ), NOW()) " +
            "ON CONFLICT (endpoint_id) DO UPDATE SET " +
            "cpu_model = EXCLUDED.cpu_model, cpu_physical = EXCLUDED.cpu_physical, " +
            "cpu_logical = EXCLUDED.cpu_logical, cpu_freq_mhz = EXCLUDED.cpu_freq_mhz, " +
            "ram_total = EXCLUDED.ram_total, swap_total = EXCLUDED.swap_total, " +
            "disks = EXCLUDED.disks, nics = EXCLUDED.nics, " +
            "collected_at = EXCLUDED.collected_at, updated_at = NOW()",
            info.endpointId(), info.cpuModel(), info.cpuPhysical(), info.cpuLogical(),
            info.cpuFreqMhz(), info.ramTotal(), info.swapTotal(),
            toJson(info.disks()), toJson(info.nics()), info.collectedAt().toString()
        );
    }

    public Map<String, Object> findSystemInfo(UUID endpointId) {
        var rows = jdbc.queryForList(
            "SELECT cpu_model, cpu_physical, cpu_logical, cpu_freq_mhz, " +
            "ram_total, swap_total, disks, nics, collected_at " +
            "FROM endpoint_system_info WHERE endpoint_id = ?", endpointId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String toJson(Object value) {
        if (value == null) return "{}";
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("serializing json: " + e.getMessage(), e);
        }
    }

    public record MetricSampleInput(String type, double value, Instant sampledAt,
                                     Map<String, String> labels) {
        public MetricSampleInput {
            if (labels == null) labels = Map.of();
        }
        public MetricSampleInput(String type, double value, Instant sampledAt) {
            this(type, value, sampledAt, Map.of());
        }
    }

    public record SystemInfoInput(
        UUID endpointId,
        String cpuModel,
        Integer cpuPhysical,
        Integer cpuLogical,
        Double cpuFreqMhz,
        Long ramTotal,
        Long swapTotal,
        List<?> disks,
        List<?> nics,
        Instant collectedAt
    ) {}
}
