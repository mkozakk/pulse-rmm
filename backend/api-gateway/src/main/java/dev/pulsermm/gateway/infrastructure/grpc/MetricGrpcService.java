package dev.pulsermm.gateway.infrastructure.grpc;

import dev.pulsermm.proto.v1.Ack;
import dev.pulsermm.proto.v1.MetricBatch;
import dev.pulsermm.proto.v1.MetricServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@GrpcService
public class MetricGrpcService extends MetricServiceGrpc.MetricServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(MetricGrpcService.class);

    private final RestClient metricClient;

    public MetricGrpcService(@Value("${METRIC_SERVICE_URL:http://localhost:8082}") String metricUrl) {
        this.metricClient = RestClient.builder().baseUrl(metricUrl).build();
    }

    @Override
    public void pushMetrics(MetricBatch request, StreamObserver<Ack> responseObserver) {
        try {
            List<Map<String, Object>> samples = request.getSamplesList().stream()
                .map(s -> Map.<String, Object>of(
                    "type", s.getType(),
                    "value", s.getValue(),
                    "collectedAt", s.getCollectedAt()
                ))
                .toList();

            metricClient.post()
                .uri("/internal/metrics")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("endpointId", request.getEndpointId(), "samples", samples))
                .retrieve()
                .toBodilessEntity();

            responseObserver.onNext(Ack.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("pushMetrics failed for {}: {}", request.getEndpointId(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("push failed").asRuntimeException());
        }
    }
}
