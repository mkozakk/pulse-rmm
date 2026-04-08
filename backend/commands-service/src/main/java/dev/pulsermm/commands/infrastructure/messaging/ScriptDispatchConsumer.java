package dev.pulsermm.commands.infrastructure.messaging;

import dev.pulsermm.commands.infrastructure.AgentHubClient;
import dev.pulsermm.commands.infrastructure.config.ScriptDispatchRabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ScriptDispatchConsumer {

    private static final Logger log = LoggerFactory.getLogger(ScriptDispatchConsumer.class);

    private final AgentHubClient agentHubClient;

    public ScriptDispatchConsumer(AgentHubClient agentHubClient) {
        this.agentHubClient = agentHubClient;
    }

    @RabbitListener(queues = ScriptDispatchRabbitConfig.QUEUE)
    public void dispatch(ScriptDispatchMessage message) {
        log.debug("Dispatching script command {} to endpoint {}", message.commandId(), message.endpointId());
        agentHubClient.dispatchScriptCommand(
                message.endpointId(),
                message.commandId(),
                message.scriptBody(),
                message.envVars(),
                message.callbackUrl()
        );
    }
}
