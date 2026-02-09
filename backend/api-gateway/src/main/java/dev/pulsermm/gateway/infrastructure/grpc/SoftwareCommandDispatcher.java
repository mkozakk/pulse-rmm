package dev.pulsermm.gateway.infrastructure.grpc;

import dev.pulsermm.proto.v1.GatewayCommand;
import dev.pulsermm.proto.v1.SoftwareCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SoftwareCommandDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(SoftwareCommandDispatcher.class);

    private final AgentRegistry agentRegistry;
    private final PendingCommandRegistry pendingCommandRegistry;
    private final String softwareServiceUrl;

    public SoftwareCommandDispatcher(AgentRegistry agentRegistry,
                                     PendingCommandRegistry pendingCommandRegistry,
                                     @Value("${SOFTWARE_SERVICE_URL:http://localhost:8085}") String softwareServiceUrl) {
        this.agentRegistry = agentRegistry;
        this.pendingCommandRegistry = pendingCommandRegistry;
        this.softwareServiceUrl = softwareServiceUrl;
    }

    public void dispatch(UUID endpointId, String commandId, String action, String name, String version) {
        logger.info("SoftwareCommandDispatcher.dispatch() called: endpoint={}, command={}, action={}", endpointId, commandId, action);
        var agent = agentRegistry.get(endpointId);
        if (agent.isEmpty()) {
            logger.warn("Agent {} not connected, cannot dispatch software command {}", endpointId, commandId);
            return;
        }
        logger.info("Agent {} found, dispatching software command {}", endpointId, commandId);

        var softwareCmd = SoftwareCommand.newBuilder()
                .setCommandId(commandId)
                .setAction(action)
                .setName(name)
                .setVersion(version != null ? version : "")
                .build();

        var gatewayCmd = GatewayCommand.newBuilder()
                .setSoftwareCommand(softwareCmd)
                .build();

        String callbackUrl = softwareServiceUrl + "/internal/commands/" + commandId + "/ack";
        pendingCommandRegistry.register(commandId, callbackUrl);

        try {
            agent.get().onNext(gatewayCmd);
            logger.debug("Dispatched software command {} ({} {}) to {}", commandId, action, name, endpointId);
        } catch (Exception e) {
            pendingCommandRegistry.remove(commandId);
            logger.error("Failed to dispatch software command {} to {}: {}", commandId, endpointId, e.getMessage());
        }
    }
}
