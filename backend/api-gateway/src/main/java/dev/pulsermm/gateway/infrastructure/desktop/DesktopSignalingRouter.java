package dev.pulsermm.gateway.infrastructure.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DesktopSignalingRouter {

    private static final Logger logger = LoggerFactory.getLogger(DesktopSignalingRouter.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, WebSocketSession ws) {
        sessions.put(sessionId, ws);
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public void forward(String sessionId, String json) {
        WebSocketSession ws = sessions.get(sessionId);
        if (ws == null || !ws.isOpen()) return;
        try {
            ws.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            logger.warn("Failed to forward signal to session {}: {}", sessionId, e.getMessage());
        }
    }
}
