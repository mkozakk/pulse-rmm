package dev.pulsermm.gateway.infrastructure.grpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SoftwareCommandDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(SoftwareCommandDispatcher.class);

    private final AgentRegistry agentRegistry;

    public SoftwareCommandDispatcher(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    public void dispatch(UUID endpointId, String commandId, String action, String name, String version) {
        logger.debug("SoftwareCommandDispatcher.dispatch() - not yet implemented for sprint 8+");
    }
}
