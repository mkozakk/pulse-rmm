package dev.pulsermm.metric.api;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Metrics", description = "Query historical endpoint metrics")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class MetricController {

    private final MetricIngestionService metricService;

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

    public record MetricPointResponse(
        @Schema(description = "Sample timestamp", example = "2026-01-01T00:00:00Z")
        Instant sampledAt,
        @Schema(description = "Metric value", example = "42.5")
        double value
    ) {}
}
