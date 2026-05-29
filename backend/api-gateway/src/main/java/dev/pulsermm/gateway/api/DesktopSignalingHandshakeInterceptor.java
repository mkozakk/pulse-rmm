package dev.pulsermm.gateway.api;

import dev.pulsermm.gateway.infrastructure.desktop.DesktopSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class DesktopSignalingHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(DesktopSignalingHandshakeInterceptor.class);

    private final DesktopSessionRegistry sessionRegistry;

    public DesktopSignalingHandshakeInterceptor(DesktopSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth instanceof JwtAuthenticationToken)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String path = request.getURI().getPath();
        // path: /ws/sessions/{sessionId}/signal
        String[] parts = path.split("/");
        if (parts.length < 4) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        String sessionId = parts[parts.length - 2];

        var info = sessionRegistry.get(sessionId);
        if (info.isEmpty()) {
            logger.debug("Session {} not found during WS handshake", sessionId);
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }

        attributes.put(DesktopSignalingWebSocketHandler.ATTR_SESSION_ID, sessionId);
        attributes.put(DesktopSignalingWebSocketHandler.ATTR_USER_ID, auth.getName());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }
}
