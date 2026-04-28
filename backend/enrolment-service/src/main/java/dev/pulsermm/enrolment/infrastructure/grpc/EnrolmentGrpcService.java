package dev.pulsermm.enrolment.infrastructure.grpc;

import dev.pulsermm.enrolment.api.InvalidTokenException;
import dev.pulsermm.enrolment.application.EnrolService;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import dev.pulsermm.proto.v1.AgentServiceGrpc;
import dev.pulsermm.proto.v1.EnrolRequest;
import dev.pulsermm.proto.v1.EnrolResponse;
import dev.pulsermm.proto.v1.HeartbeatOk;
import dev.pulsermm.proto.v1.HeartbeatRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.transaction.Transactional;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

@GrpcService
public class EnrolmentGrpcService extends AgentServiceGrpc.AgentServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(EnrolmentGrpcService.class);
    private final EnrolService enrolService;
    private final EndpointRepository endpointRepository;

    public EnrolmentGrpcService(EnrolService enrolService, EndpointRepository endpointRepository) {
        this.enrolService = enrolService;
        this.endpointRepository = endpointRepository;
    }

    @Override
    public void enrol(EnrolRequest request, StreamObserver<EnrolResponse> responseObserver) {
        try {
            UUID tokenId = UUID.fromString(request.getToken());
            byte[] publicKey = request.getPublicKey().toByteArray();
            String hostname = request.getHostname();
            String os = request.getOs();
            String arch = request.getArch();

            UUID endpointId = enrolService.enrol(tokenId, publicKey, hostname, os, arch);

            EnrolResponse response = EnrolResponse.newBuilder()
                .setEndpointId(endpointId.toString())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (InvalidTokenException e) {
            logger.debug("Invalid token in enrol request: {}", e.getMessage());
            responseObserver.onError(Status.UNAUTHENTICATED
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (Exception e) {
            logger.error("Enrol failed", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Enrol failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    @Transactional
    public void heartbeat(HeartbeatRequest request, StreamObserver<HeartbeatOk> responseObserver) {
        try {
            UUID endpointId = UUID.fromString(request.getEndpointId());
            endpointRepository.findById(endpointId).ifPresentOrElse(
                endpoint -> {
                    endpoint.setLastSeen(Instant.now());
                    endpointRepository.save(endpoint);
                    responseObserver.onNext(HeartbeatOk.newBuilder().build());
                    responseObserver.onCompleted();
                },
                () -> responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Endpoint not found")
                    .asRuntimeException())
            );
        } catch (Exception e) {
            logger.error("Heartbeat failed", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Heartbeat failed: " + e.getMessage())
                .asRuntimeException());
        }
    }
}
