package dev.pulsermm.agenthub.infrastructure.grpc;

import dev.pulsermm.proto.v1.GatewayCommand;
import dev.pulsermm.proto.v1.ScriptCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class ScriptCommandDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(ScriptCommandDispatcher.class);

    private final AgentRegistry agentRegistry;

    public ScriptCommandDispatcher(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    public void dispatch(UUID endpointId, String commandId, String scriptBody, Map<String, String> envVars) {
        var agent = agentRegistry.get(endpointId);
        if (agent.isEmpty()) {
            logger.warn("Agent {} not connected, cannot dispatch script command {}", endpointId, commandId);
            return;
        }

        var scriptCmdBuilder = ScriptCommand.newBuilder()
            .setCommandId(commandId)
            .setScriptBody(scriptBody);

        if (envVars != null) {
            scriptCmdBuilder.putAllEnvVars(envVars);
        }

        var gatewayCmd = GatewayCommand.newBuilder()
            .setScriptCommand(scriptCmdBuilder.build())
            .build();

        try {
            agent.get().onNext(gatewayCmd);
            logger.debug("Dispatched script command {} to {}", commandId, endpointId);
        } catch (Exception e) {
            logger.error("Failed to dispatch script command {} to {}: {}", commandId, endpointId, e.getMessage());
        }
    }
}
