package dev.pulsermm.gateway.infrastructure.grpc;

import dev.pulsermm.proto.v1.*;
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
public class AgentServiceGrpcServer extends AgentServiceGrpc.AgentServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(AgentServiceGrpcServer.class);

    private final RestClient restClient;
    private final String softwareServiceUrl;

    public AgentServiceGrpcServer() {
        String url = System.getenv("SOFTWARE_SERVICE_URL");
        if (url == null) {
            url = "http://localhost:8085";
        }
        this.restClient = RestClient.builder()
                .baseUrl(url)
                .build();
        this.softwareServiceUrl = url;
        logger.info("AgentServiceGrpcServer initialized with software service URL: {}", softwareServiceUrl);
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setStatus("pong").build());
        responseObserver.onCompleted();
    }

    @Override
    public void enrol(EnrolRequest request, StreamObserver<EnrolResponse> responseObserver) {
        responseObserver.onError(Status.UNIMPLEMENTED.asException());
    }

    @Override
    public void heartbeat(HeartbeatRequest request, StreamObserver<HeartbeatOk> responseObserver) {
        responseObserver.onNext(HeartbeatOk.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void pushSoftwareList(SoftwareList request, StreamObserver<Ack> responseObserver) {
        try {
            List<Map<String, String>> items = request.getItemsList().stream()
                    .map(i -> Map.of("name", i.getName(), "version", i.getVersion(), "source", i.getSource()))
                    .toList();

            restClient.post()
                    .uri(softwareServiceUrl + "/internal/software-list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("endpointId", request.getEndpointId(), "items", items))
                    .retrieve()
                    .toBodilessEntity();

            logger.debug("Forwarded software list for endpoint {}: {} items",
                    request.getEndpointId(), items.size());

            responseObserver.onNext(Ack.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Failed to forward software list for {}: {}", request.getEndpointId(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
