package dev.pulsermm.metric.infrastructure.grpc;

import dev.pulsermm.metric.application.MetricIngestionService;
import dev.pulsermm.metric.application.MetricIngestionService.MetricSampleInput;
import dev.pulsermm.proto.v1.Ack;
import dev.pulsermm.proto.v1.MetricBatch;
import dev.pulsermm.proto.v1.MetricServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@GrpcService
public class MetricGrpcService extends MetricServiceGrpc.MetricServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(MetricGrpcService.class);

    private final MetricIngestionService metricService;

    public MetricGrpcService(MetricIngestionService metricService) {
        this.metricService = metricService;
    }

    @Override
    public void pushMetrics(MetricBatch request, StreamObserver<Ack> responseObserver) {
        try {
            UUID endpointId = UUID.fromString(request.getEndpointId());
            List<MetricSampleInput> samples = request.getSamplesList().stream()
                .map(s -> new MetricSampleInput(
                    s.getType(),
                    s.getValue(),
                    Instant.ofEpochMilli(s.getCollectedAt())
                ))
                .toList();

            metricService.pushMetrics(endpointId, samples);
            responseObserver.onNext(Ack.newBuilder().build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("invalid endpoint_id")
                .asRuntimeException());
        } catch (Exception e) {
            logger.error("PushMetrics failed", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("push failed")
                .asRuntimeException());
        }
    }
}
