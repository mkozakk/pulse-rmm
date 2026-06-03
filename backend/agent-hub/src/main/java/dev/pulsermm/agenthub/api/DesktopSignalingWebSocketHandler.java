package dev.pulsermm.agenthub.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.agenthub.infrastructure.desktop.DesktopSessionDispatcher;
import dev.pulsermm.agenthub.infrastructure.desktop.DesktopSessionRegistry;
import dev.pulsermm.agenthub.infrastructure.desktop.DesktopSignalingRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class DesktopSignalingWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(DesktopSignalingWebSocketHandler.class);

    static final String ATTR_SESSION_ID = "desktopSessionId";
    static final String ATTR_USER_ID = "userId";

    private final DesktopSignalingRouter router;
    private final DesktopSessionRegistry sessionRegistry;
    private final DesktopSessionDispatcher dispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DesktopSignalingWebSocketHandler(DesktopSignalingRouter router,
                                             DesktopSessionRegistry sessionRegistry,
                                             DesktopSessionDispatcher dispatcher) {
        this.router = router;
        this.sessionRegistry = sessionRegistry;
        this.dispatcher = dispatcher;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) {
        String sessionId = (String) ws.getAttributes().get(ATTR_SESSION_ID);
        var info = sessionRegistry.get(sessionId);
        if (info.isEmpty()) {
            closeQuietly(ws, new CloseStatus(4001, "session not found"));
            return;
        }
        router.register(sessionId, ws);
        logger.debug("Signaling WS opened for session {}", sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage message) throws Exception {
        String sessionId = (String) ws.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId == null) return;

        var info = sessionRegistry.get(sessionId);
        if (info.isEmpty()) {
            closeQuietly(ws, new CloseStatus(4001, "session not found"));
            return;
        }

        JsonNode node = objectMapper.readTree(message.getPayload());
        String type = node.path("type").asText();
        String payload = node.path("payload").asText();

        dispatcher.forwardSignal(info.get().endpointId(), sessionId, type, payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        String sessionId = (String) ws.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId == null) return;

        router.remove(sessionId);

        sessionRegistry.get(sessionId).ifPresent(info ->
            dispatcher.endSession(info.endpointId(), sessionId)
        );
        logger.debug("Signaling WS closed for session {}: {}", sessionId, status);
    }

    private void closeQuietly(WebSocketSession ws, CloseStatus status) {
        try {
            ws.close(status);
        } catch (Exception e) {
            logger.debug("Error closing WS: {}", e.getMessage());
        }
    }
}
