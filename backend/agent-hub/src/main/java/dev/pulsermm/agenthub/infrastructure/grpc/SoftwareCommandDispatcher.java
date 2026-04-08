package dev.pulsermm.agenthub.infrastructure.grpc;

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
                                     @Value("${COMMANDS_SERVICE_URL:http://localhost:8084}") String softwareServiceUrl) {
        this.agentRegistry = agentRegistry;
        this.pendingCommandRegistry = pendingCommandRegistry;
        this.softwareServiceUrl = softwareServiceUrl;
    }

    public void dispatch(UUID endpointId, String commandId, String action, String name, String appId, String version) {
        var agent = agentRegistry.get(endpointId);
        if (agent.isEmpty()) {
            logger.warn("Agent {} not connected, cannot dispatch software command {}", endpointId, commandId);
            return;
        }

        var softwareCmd = SoftwareCommand.newBuilder()
                .setCommandId(commandId)
                .setAction(action)
                .setName(name)
                .setId(appId != null ? appId : "")
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
