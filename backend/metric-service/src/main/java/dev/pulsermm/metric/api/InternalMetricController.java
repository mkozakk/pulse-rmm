package dev.pulsermm.metric.api;

import dev.pulsermm.metric.application.MetricIngestionService;
import dev.pulsermm.metric.application.MetricIngestionService.MetricSampleInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal")
public class InternalMetricController {

    private static final Logger logger = LoggerFactory.getLogger(InternalMetricController.class);

    private final MetricIngestionService metricService;

    public InternalMetricController(MetricIngestionService metricService) {
        this.metricService = metricService;
    }

    record SampleInput(String type, double value, long collectedAt) {}
    record MetricRequest(String endpointId, List<SampleInput> samples) {}
    record HeartbeatRequest(String endpointId) {}

    @PostMapping("/metrics")
    public ResponseEntity<Void> pushMetrics(@RequestBody MetricRequest req) {
        try {
            UUID endpointId = UUID.fromString(req.endpointId());
            Instant ingestedAt = Instant.now();
            List<MetricSampleInput> samples = req.samples().stream()
                .map(s -> new MetricSampleInput(s.type(), s.value(), ingestedAt))
                .toList();
            metricService.pushMetrics(endpointId, samples);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Internal push metrics failed for {}", req.endpointId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@RequestBody HeartbeatRequest req) {
        try {
            UUID endpointId = UUID.fromString(req.endpointId());
            metricService.heartbeat(endpointId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Internal heartbeat failed for {}", req.endpointId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
