package dev.pulsermm.enrolment.infrastructure.grpc;

import dev.pulsermm.enrolment.api.InvalidTokenException;
import dev.pulsermm.enrolment.application.EnrolService;
import dev.pulsermm.proto.v1.AgentServiceGrpc;
import dev.pulsermm.proto.v1.EnrolRequest;
import dev.pulsermm.proto.v1.EnrolResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Service
public class EnrolmentGrpcService extends AgentServiceGrpc.AgentServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(EnrolmentGrpcService.class);
    private final EnrolService enrolService;

    public EnrolmentGrpcService(EnrolService enrolService) {
        this.enrolService = enrolService;
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
}
