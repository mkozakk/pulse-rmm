package dev.pulsermm.metric.infrastructure.grpc;

import dev.pulsermm.metric.application.MetricIngestionService;
import dev.pulsermm.proto.v1.AgentServiceGrpc;
import dev.pulsermm.proto.v1.HeartbeatOk;
import dev.pulsermm.proto.v1.HeartbeatRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@GrpcService
public class HeartbeatGrpcService extends AgentServiceGrpc.AgentServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatGrpcService.class);

    private final MetricIngestionService metricService;

    public HeartbeatGrpcService(MetricIngestionService metricService) {
        this.metricService = metricService;
    }

    @Override
    public void heartbeat(HeartbeatRequest request, StreamObserver<HeartbeatOk> responseObserver) {
        try {
            UUID endpointId = UUID.fromString(request.getEndpointId());
            metricService.heartbeat(endpointId);
            responseObserver.onNext(HeartbeatOk.newBuilder().build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("invalid endpoint_id")
                .asRuntimeException());
        } catch (Exception e) {
            logger.error("Heartbeat failed", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("heartbeat failed")
                .asRuntimeException());
        }
    }
}
