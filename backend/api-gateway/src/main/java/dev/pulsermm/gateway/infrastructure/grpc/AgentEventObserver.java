package dev.pulsermm.gateway.infrastructure.grpc;

import dev.pulsermm.gateway.infrastructure.ws.ShellSessionRouter;
import dev.pulsermm.proto.v1.AgentEvent;
import dev.pulsermm.proto.v1.GatewayCommand;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

class AgentEventObserver implements StreamObserver<AgentEvent> {

    private static final Logger logger = LoggerFactory.getLogger(AgentEventObserver.class);

    private final AgentRegistry registry;
    private final ShellSessionRouter router;
    private final PendingCommandRegistry pendingCommandRegistry;
    private final RestClient restClient;
    private final StreamObserver<GatewayCommand> outbound;
    private UUID endpointId;

    AgentEventObserver(AgentRegistry registry, ShellSessionRouter router, PendingCommandRegistry pendingCommandRegistry,
                       StreamObserver<GatewayCommand> outbound) {
        this.registry = registry;
        this.router = router;
        this.pendingCommandRegistry = pendingCommandRegistry;
        this.restClient = RestClient.create();
        this.outbound = outbound;
    }

    @Override
    public void onNext(AgentEvent event) {
        if (endpointId == null) {
            if (!event.hasHello()) {
                outbound.onError(Status.INVALID_ARGUMENT
                    .withDescription("First message must be AgentHello")
                    .asRuntimeException());
                return;
            }
            endpointId = UUID.fromString(event.getHello().getEndpointId());
            registry.register(endpointId, outbound);
            logger.info("Agent registered: {}", endpointId);
            return;
        }

        if (event.hasShellOutput()) {
            router.route(event.getShellOutput().getSessionId(), event);
        } else if (event.hasShellExited()) {
            router.route(event.getShellExited().getSessionId(), event);
        } else if (event.hasAckCommand()) {
            var ack = event.getAckCommand();
            pendingCommandRegistry.remove(ack.getCommandId()).ifPresent(callbackUrl -> {
                try {
                    restClient.post()
                        .uri(callbackUrl)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(Map.of("exitCode", ack.getExitCode(), "output", ack.getOutput()))
                        .retrieve()
                        .toBodilessEntity();
                    logger.debug("Acked command {} for endpoint {}", ack.getCommandId(), endpointId);
                } catch (Exception e) {
                    logger.error("Failed to ack command {} to {}: {}", ack.getCommandId(), callbackUrl, e.getMessage());
                }
            });
        } else {
            logger.debug("Agent event from {}: {}", endpointId, event.getPayloadCase());
        }
    }

    @Override
    public void onError(Throwable t) {
        if (endpointId != null) {
            registry.unregister(endpointId);
            logger.info("Agent disconnected (error): {}", endpointId);
        }
    }

    @Override
    public void onCompleted() {
        if (endpointId != null) {
            registry.unregister(endpointId);
            logger.info("Agent disconnected: {}", endpointId);
        }
        outbound.onCompleted();
    }
}
