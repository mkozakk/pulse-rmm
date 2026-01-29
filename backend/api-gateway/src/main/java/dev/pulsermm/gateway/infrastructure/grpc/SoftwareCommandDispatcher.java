package dev.pulsermm.gateway.infrastructure.grpc;

import dev.pulsermm.proto.v1.GatewayCommand;
import dev.pulsermm.proto.v1.SoftwareCommand;
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
        var agent = agentRegistry.get(endpointId);
        if (agent.isEmpty()) {
            logger.warn("Agent {} not connected, cannot dispatch software command {}", endpointId, commandId);
            return;
        }

        var softwareCmd = SoftwareCommand.newBuilder()
            .setCommandId(commandId)
            .setAction(action)
            .setName(name)
            .setVersion(version != null ? version : "")
            .build();

        var gatewayCmd = GatewayCommand.newBuilder()
            .setSoftwareCommand(softwareCmd)
            .build();

        try {
            agent.get().onNext(gatewayCmd);
            logger.debug("Dispatched software command {} to {}", commandId, endpointId);
        } catch (Exception e) {
            logger.error("Failed to dispatch software command {} to {}: {}", commandId, endpointId, e.getMessage());
        }
    }
}
