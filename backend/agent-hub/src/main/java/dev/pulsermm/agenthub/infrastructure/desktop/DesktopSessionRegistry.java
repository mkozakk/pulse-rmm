package dev.pulsermm.agenthub.infrastructure.desktop;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DesktopSessionRegistry {

    // technicianId is optional — may be null if not provided by the caller
    public record SessionInfo(UUID endpointId, UUID technicianId) {}

    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, UUID endpointId, UUID technicianId) {
        sessions.put(sessionId, new SessionInfo(endpointId, technicianId));
    }

    public Optional<SessionInfo> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }
}
