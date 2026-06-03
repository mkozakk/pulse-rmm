package dev.pulsermm.agenthub.infrastructure.ws;

import dev.pulsermm.proto.v1.AgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ShellSessionRouter {

    private static final Logger logger = LoggerFactory.getLogger(ShellSessionRouter.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, WebSocketSession ws) {
        sessions.put(sessionId, ws);
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public void route(String sessionId, AgentEvent event) {
        WebSocketSession ws = sessions.get(sessionId);
        if (ws == null) return;

        try {
            if (event.hasShellOutput()) {
                byte[] data = event.getShellOutput().getData().toByteArray();
                byte[] frame = new byte[data.length + 1];
                frame[0] = 0x01;
                System.arraycopy(data, 0, frame, 1, data.length);
                ws.sendMessage(new BinaryMessage(frame));
            } else if (event.hasShellExited()) {
                String reason = event.getShellExited().getError();
                ws.close(reason.isEmpty() ? CloseStatus.NORMAL : CloseStatus.SERVER_ERROR.withReason(reason));
                sessions.remove(sessionId);
            }
        } catch (IOException e) {
            logger.warn("Failed to forward event to WS session {}: {}", sessionId, e.getMessage());
        }
    }
}
