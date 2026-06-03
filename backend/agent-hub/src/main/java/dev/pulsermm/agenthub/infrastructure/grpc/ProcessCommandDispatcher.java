package dev.pulsermm.agenthub.infrastructure.grpc;

import dev.pulsermm.proto.v1.GatewayCommand;
import dev.pulsermm.proto.v1.KillProcessCommand;
import dev.pulsermm.proto.v1.ListProcessesCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProcessCommandDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(ProcessCommandDispatcher.class);

    private final AgentRegistry agentRegistry;

    public ProcessCommandDispatcher(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    public void dispatchList(UUID endpointId, String commandId) {
        var agent = agentRegistry.get(endpointId);
        if (agent.isEmpty()) {
            logger.warn("Agent {} not connected, cannot dispatch list-processes {}", endpointId, commandId);
            return;
        }
        var cmd = GatewayCommand.newBuilder()
            .setListProcesses(ListProcessesCommand.newBuilder().setCommandId(commandId).build())
            .build();
        try {
            agent.get().onNext(cmd);
        } catch (Exception e) {
            logger.error("Failed to dispatch list-processes {} to {}: {}", commandId, endpointId, e.getMessage());
        }
    }

    public void dispatchKill(UUID endpointId, String commandId, int pid) {
        var agent = agentRegistry.get(endpointId);
        if (agent.isEmpty()) {
            logger.warn("Agent {} not connected, cannot dispatch kill-process {}", endpointId, commandId);
            return;
        }
        var cmd = GatewayCommand.newBuilder()
            .setKillProcess(KillProcessCommand.newBuilder()
                .setCommandId(commandId)
                .setPid(pid)
                .build())
            .build();
        try {
            agent.get().onNext(cmd);
        } catch (Exception e) {
            logger.error("Failed to dispatch kill-process {} to {}: {}", commandId, endpointId, e.getMessage());
        }
    }
}
