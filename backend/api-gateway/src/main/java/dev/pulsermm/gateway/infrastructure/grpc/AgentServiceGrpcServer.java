package dev.pulsermm.gateway.infrastructure.grpc;

import dev.pulsermm.gateway.infrastructure.mtls.MtlsContext;
import dev.pulsermm.proto.v1.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GrpcService
public class AgentServiceGrpcServer extends AgentServiceGrpc.AgentServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(AgentServiceGrpcServer.class);

    private final RestClient enrolmentClient;
    private final RestClient metricClient;
    private final RestClient softwareClient;

    public AgentServiceGrpcServer(
            @Value("${ENDPOINT_SERVICE_URL:http://localhost:8081}") String enrolmentUrl,
            @Value("${METRIC_SERVICE_URL:http://localhost:8082}") String metricUrl,
            @Value("${COMMANDS_SERVICE_URL:http://localhost:8084}") String softwareUrl) {
        this.enrolmentClient = RestClient.builder().baseUrl(enrolmentUrl).build();
        this.metricClient = RestClient.builder().baseUrl(metricUrl).build();
        this.softwareClient = RestClient.builder().baseUrl(softwareUrl).build();
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setStatus("pong").build());
        responseObserver.onCompleted();
    }

    @Override
    public void enrol(EnrolRequest request, StreamObserver<EnrolResponse> responseObserver) {
        try {
            String publicKeyB64 = Base64.getEncoder().encodeToString(request.getPublicKey().toByteArray());
            java.util.HashMap<String, String> body = new java.util.HashMap<>();
            body.put("token", request.getToken());
            body.put("publicKey", publicKeyB64);
            body.put("hostname", request.getHostname());
            body.put("os", request.getOs());
            body.put("arch", request.getArch());
            if (request.getCsrPem() != null && !request.getCsrPem().isEmpty()) {
                body.put("csrPem", request.getCsrPem());
            }

            var result = enrolmentClient.post()
                .uri("/internal/enrol")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(Map.class);

            if (result.getStatusCode().is2xxSuccessful() && result.getBody() != null) {
                Map<?, ?> resp = result.getBody();
                EnrolResponse.Builder respBuilder = EnrolResponse.newBuilder()
                    .setEndpointId((String) resp.get("endpointId"));
                Object certPem = resp.get("certPem");
                Object caPem = resp.get("caBundlePem");
                if (certPem instanceof String s && !s.isEmpty()) {
                    respBuilder.setClientCertPem(com.google.protobuf.ByteString.copyFromUtf8(s));
                }
                if (caPem instanceof String s && !s.isEmpty()) {
                    respBuilder.setCaCertPem(com.google.protobuf.ByteString.copyFromUtf8(s));
                }
                responseObserver.onNext(respBuilder.build());
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(Status.INTERNAL.withDescription("enrol failed").asRuntimeException());
            }
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            responseObserver.onError(Status.UNAUTHENTICATED.withDescription("invalid token").asRuntimeException());
        } catch (Exception e) {
            logger.error("Enrol failed", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void renewCert(RenewCertRequest request, StreamObserver<RenewCertResponse> responseObserver) {
        UUID endpointId = MtlsContext.ENDPOINT_ID.get();
        if (endpointId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                .withDescription("client certificate required").asRuntimeException());
            return;
        }
        if (request.getCsrPem() == null || request.getCsrPem().isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("csr_pem is required").asRuntimeException());
            return;
        }
        try {
            var result = enrolmentClient.post()
                .uri("/internal/cert/renew")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("endpointId", endpointId.toString(), "csrPem", request.getCsrPem()))
                .retrieve()
                .toEntity(Map.class);
            Map<?, ?> body = result.getBody();
            if (body == null) {
                responseObserver.onError(Status.INTERNAL.withDescription("empty response").asRuntimeException());
                return;
            }
            RenewCertResponse.Builder b = RenewCertResponse.newBuilder();
            if (body.get("certPem") instanceof String s) {
                b.setClientCertPem(com.google.protobuf.ByteString.copyFromUtf8(s));
            }
            if (body.get("caBundlePem") instanceof String s) {
                b.setCaCertPem(com.google.protobuf.ByteString.copyFromUtf8(s));
            }
            responseObserver.onNext(b.build());
            responseObserver.onCompleted();
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            responseObserver.onError(Status.PERMISSION_DENIED
                .withDescription("endpoint revoked").asRuntimeException());
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription("endpoint not found").asRuntimeException());
        } catch (Exception e) {
            logger.error("RenewCert failed for {}", endpointId, e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void heartbeat(HeartbeatRequest request, StreamObserver<HeartbeatOk> responseObserver) {
        Map<String, String> body = Map.of("endpointId", request.getEndpointId());
        try {
            enrolmentClient.post().uri("/internal/heartbeat")
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
        } catch (Exception e) {
            logger.warn("Enrolment heartbeat failed for {}: {}", request.getEndpointId(), e.getMessage());
        }
        try {
            metricClient.post().uri("/internal/heartbeat")
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
        } catch (Exception e) {
            logger.warn("Metric heartbeat failed for {}: {}", request.getEndpointId(), e.getMessage());
        }

        responseObserver.onNext(HeartbeatOk.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void pushSoftwareList(SoftwareList request, StreamObserver<Ack> responseObserver) {
        try {
            List<Map<String, Object>> items = request.getItemsList().stream()
                .map(i -> Map.<String, Object>of(
                    "name", i.getName(),
                    "id", i.getId(),
                    "version", i.getVersion(),
                    "updateTo", i.getUpdateTo(),
                    "isStore", i.getIsStore(),
                    "source", i.getSource()
                ))
                .toList();

            softwareClient.post()
                .uri("/internal/software-list")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("endpointId", request.getEndpointId(), "items", items))
                .retrieve()
                .toBodilessEntity();

            responseObserver.onNext(Ack.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("pushSoftwareList failed for {}: {}", request.getEndpointId(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
