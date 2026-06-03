package dev.pulsermm.agenthub.infrastructure.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class DesktopSignalingRouter {

    private static final Logger logger = LoggerFactory.getLogger(DesktopSignalingRouter.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Queue<String>> pending = new ConcurrentHashMap<>();

    public void register(String sessionId, WebSocketSession ws) {
        sessions.put(sessionId, ws);
        Queue<String> buffered = pending.remove(sessionId);
        if (buffered != null) {
            for (String msg : buffered) {
                try {
                    if (ws.isOpen()) ws.sendMessage(new TextMessage(msg));
                } catch (IOException e) {
                    logger.warn("Failed to flush buffered message to session {}: {}", sessionId, e.getMessage());
                }
            }
        }
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
        pending.remove(sessionId);
    }

    public void forward(String sessionId, String json) {
        WebSocketSession ws = sessions.get(sessionId);
        if (ws != null && ws.isOpen()) {
            try {
                ws.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                logger.warn("Failed to forward signal to session {}: {}", sessionId, e.getMessage());
            }
            return;
        }
        // WebSocket not connected yet — buffer so it's delivered on register()
        pending.computeIfAbsent(sessionId, k -> new ConcurrentLinkedQueue<>()).add(json);
    }
}
