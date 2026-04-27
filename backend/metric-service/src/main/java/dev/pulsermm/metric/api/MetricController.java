package dev.pulsermm.metric.api;

import dev.pulsermm.metric.application.MetricIngestionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class MetricController {

    private final MetricIngestionService metricService;

    public MetricController(MetricIngestionService metricService) {
        this.metricService = metricService;
    }

    @GetMapping("/api/endpoints/{id}/metrics")
    public ResponseEntity<List<MetricPointResponse>> getMetrics(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam String type) {

        List<Map<String, Object>> rows = metricService.queryMetrics(id, from, to, type);
        List<MetricPointResponse> result = rows.stream()
            .map(row -> new MetricPointResponse(
                ((java.sql.Timestamp) row.get("sampled_at")).toInstant(),
                ((Number) row.get("value")).doubleValue()
            ))
            .toList();

        return ResponseEntity.ok(result);
    }

    public record MetricPointResponse(Instant sampledAt, double value) {}
}
