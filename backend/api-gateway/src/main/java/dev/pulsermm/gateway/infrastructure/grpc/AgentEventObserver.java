package dev.pulsermm.gateway.infrastructure.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.gateway.infrastructure.desktop.DesktopSignalingRouter;
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
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AgentRegistry registry;
    private final ShellSessionRouter router;
    private final DesktopSignalingRouter signalingRouter;
    private final PendingCommandRegistry pendingCommandRegistry;
    private final RestClient restClient;
    private final StreamObserver<GatewayCommand> outbound;
    private UUID endpointId;

    AgentEventObserver(AgentRegistry registry, ShellSessionRouter router, DesktopSignalingRouter signalingRouter,
                       PendingCommandRegistry pendingCommandRegistry,
                       StreamObserver<GatewayCommand> outbound) {
        this.registry = registry;
        this.router = router;
        this.signalingRouter = signalingRouter;
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
        } else if (event.hasSessionReady()) {
            var ready = event.getSessionReady();
            String json;
            if (ready.getError().isEmpty()) {
                json = "{\"type\":\"session_ready\",\"session_id\":\"" + ready.getSessionId() + "\"}";
            } else {
                json = "{\"type\":\"error\",\"code\":\"" + ready.getError() + "\",\"session_id\":\"" + ready.getSessionId() + "\"}";
            }
            signalingRouter.forward(ready.getSessionId(), json);
        } else if (event.hasDesktopSignal()) {
            var signal = event.getDesktopSignal();
            try {
                String json = objectMapper.writeValueAsString(Map.of(
                    "type", signal.getType(),
                    "payload", signal.getPayload()
                ));
                signalingRouter.forward(signal.getSessionId(), json);
            } catch (Exception e) {
                logger.warn("Failed to serialize desktop signal for session {}: {}", signal.getSessionId(), e.getMessage());
            }
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
