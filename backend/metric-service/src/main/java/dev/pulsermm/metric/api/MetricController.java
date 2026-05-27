package dev.pulsermm.metric.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.metric.application.MetricIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Metrics", description = "Query historical endpoint metrics")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class MetricController {

    private final MetricIngestionService metricService;
    private final ObjectMapper json = new ObjectMapper();

    public MetricController(MetricIngestionService metricService) {
        this.metricService = metricService;
    }

    @Operation(summary = "Query endpoint metrics")
    @ApiResponse(responseCode = "200", description = "Metric points",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = MetricPointResponse.class))))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @GetMapping("/api/endpoints/{id}/metrics")
    public ResponseEntity<List<MetricPointResponse>> getMetrics(
            @PathVariable UUID id,
            @Parameter(description = "Start time (inclusive)", example = "2026-01-01T00:00:00Z")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "End time (inclusive)", example = "2026-01-01T01:00:00Z")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Metric type", example = "cpu")
            @RequestParam String type,
            @RequestParam Map<String, String> all) {

        Map<String, String> labelFilter = new HashMap<>();
        for (var e : all.entrySet()) {
            if (e.getKey().startsWith("label.")) {
                labelFilter.put(e.getKey().substring("label.".length()), e.getValue());
            }
        }

        List<Map<String, Object>> rows = metricService.queryMetrics(id, from, to, type, labelFilter);
        List<MetricPointResponse> result = rows.stream()
            .map(row -> {
                Object sampledAt = row.get("sampled_at");
                Instant instant;
                if (sampledAt instanceof java.sql.Timestamp ts) {
                    instant = ts.toInstant();
                } else if (sampledAt instanceof java.time.OffsetDateTime odt) {
                    instant = odt.toInstant();
                } else {
                    instant = Instant.parse(sampledAt.toString());
                }
                Map<String, String> labels = parseLabels(row.get("labels"));
                return new MetricPointResponse(
                    instant,
                    ((Number) row.get("value")).doubleValue(),
                    labels
                );
            })
            .toList();

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get static system info for an endpoint")
    @GetMapping("/api/endpoints/{id}/system-info")
    public ResponseEntity<?> getSystemInfo(@PathVariable UUID id) {
        Map<String, Object> row = metricService.findSystemInfo(id);
        if (row == null) {
            ProblemDetail pd = ProblemDetail.forStatus(404);
            pd.setTitle("System info not found");
            pd.setDetail("No system info has been reported for endpoint " + id);
            return ResponseEntity.status(404).body(pd);
        }

        SystemInfoResponse resp = new SystemInfoResponse(
            (String) row.get("cpu_model"),
            (Integer) row.get("cpu_physical"),
            (Integer) row.get("cpu_logical"),
            (Double) row.get("cpu_freq_mhz"),
            row.get("ram_total") == null ? null : ((Number) row.get("ram_total")).longValue(),
            row.get("swap_total") == null ? null : ((Number) row.get("swap_total")).longValue(),
            parseJsonArray(row.get("disks")),
            parseJsonArray(row.get("nics")),
            toInstant(row.get("collected_at"))
        );
        return ResponseEntity.ok(resp);
    }

    private Instant toInstant(Object v) {
        if (v == null) return null;
        if (v instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (v instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return Instant.parse(v.toString());
    }

    private Map<String, String> parseLabels(Object value) {
        if (value == null) return Map.of();
        try {
            String raw = value.toString();
            if (raw.isBlank() || raw.equals("{}")) return Map.of();
            return json.readValue(raw, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonArray(Object value) {
        if (value == null) return List.of();
        try {
            String raw = value.toString();
            if (raw.isBlank() || raw.equals("[]")) return List.of();
            return json.readValue(raw, List.class);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public record MetricPointResponse(
        @Schema(description = "Sample timestamp", example = "2026-01-01T00:00:00Z")
        Instant sampledAt,
        @Schema(description = "Metric value", example = "42.5")
        double value,
        @Schema(description = "Sample labels")
        Map<String, String> labels
    ) {}

    public record SystemInfoResponse(
        String cpuModel,
        Integer cpuPhysical,
        Integer cpuLogical,
        Double cpuFreqMhz,
        Long ramTotal,
        Long swapTotal,
        List<Map<String, Object>> disks,
        List<Map<String, Object>> nics,
        Instant collectedAt
    ) {}
}
