package dev.pulsermm.agenthub.infrastructure.desktop;

import dev.pulsermm.agenthub.infrastructure.grpc.AgentRegistry;
import dev.pulsermm.proto.v1.DesktopSignalMessage;
import dev.pulsermm.proto.v1.EndDesktopSessionCommand;
import dev.pulsermm.proto.v1.GatewayCommand;
import dev.pulsermm.proto.v1.StartDesktopSessionCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class DesktopSessionDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(DesktopSessionDispatcher.class);

    private final AgentRegistry agentRegistry;

    public DesktopSessionDispatcher(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    public void startSession(UUID endpointId, String sessionId, List<String> turnUrls, String turnSecret) {
        var agent = agentRegistry.get(endpointId);
        if (agent.isEmpty()) {
            logger.warn("Agent {} not connected, cannot start desktop session {}", endpointId, sessionId);
            return;
        }

        var cmd = StartDesktopSessionCommand.newBuilder()
                .setSessionId(sessionId)
                .addAllTurnUrls(turnUrls)
                .setTurnSecret(turnSecret)
                .build();

        try {
            agent.get().onNext(GatewayCommand.newBuilder().setStartDesktopSession(cmd).build());
            logger.debug("Dispatched StartDesktopSession {} to {}", sessionId, endpointId);
        } catch (Exception e) {
            logger.error("Failed to dispatch StartDesktopSession {} to {}: {}", sessionId, endpointId, e.getMessage());
        }
    }

    public void endSession(UUID endpointId, String sessionId) {
        var agent = agentRegistry.get(endpointId);
        if (agent.isEmpty()) {
            logger.debug("Agent {} not connected, skipping EndDesktopSession {}", endpointId, sessionId);
            return;
        }

        var cmd = EndDesktopSessionCommand.newBuilder().setSessionId(sessionId).build();
        try {
            agent.get().onNext(GatewayCommand.newBuilder().setEndDesktopSession(cmd).build());
            logger.debug("Dispatched EndDesktopSession {} to {}", sessionId, endpointId);
        } catch (Exception e) {
            logger.debug("Failed to dispatch EndDesktopSession {} to {}: {}", sessionId, endpointId, e.getMessage());
        }
    }

    public void forwardSignal(UUID endpointId, String sessionId, String type, String payload) {
        var agent = agentRegistry.get(endpointId);
        if (agent.isEmpty()) {
            logger.warn("Agent {} not connected, cannot forward signal for session {}", endpointId, sessionId);
            return;
        }

        var signal = DesktopSignalMessage.newBuilder()
                .setSessionId(sessionId)
                .setType(type)
                .setPayload(payload)
                .build();

        try {
            agent.get().onNext(GatewayCommand.newBuilder().setDesktopSignal(signal).build());
        } catch (Exception e) {
            logger.warn("Failed to forward signal to agent {}: {}", endpointId, e.getMessage());
        }
    }
}
