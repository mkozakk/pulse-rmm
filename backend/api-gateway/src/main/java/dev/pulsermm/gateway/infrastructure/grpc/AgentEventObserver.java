package dev.pulsermm.gateway.infrastructure.grpc;

import dev.pulsermm.proto.v1.AgentEvent;
import dev.pulsermm.proto.v1.GatewayCommand;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

class AgentEventObserver implements StreamObserver<AgentEvent> {

    private static final Logger logger = LoggerFactory.getLogger(AgentEventObserver.class);

    private final AgentRegistry registry;
    private final StreamObserver<GatewayCommand> outbound;
    private UUID endpointId;

    AgentEventObserver(AgentRegistry registry, StreamObserver<GatewayCommand> outbound) {
        this.registry = registry;
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

        // Phase 4+ will route shell events here
        logger.debug("Agent event from {}: {}", endpointId, event.getPayloadCase());
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
