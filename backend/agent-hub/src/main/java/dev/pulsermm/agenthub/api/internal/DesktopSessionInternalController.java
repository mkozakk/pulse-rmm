package dev.pulsermm.agenthub.api.internal;

import dev.pulsermm.agenthub.infrastructure.desktop.DesktopSessionDispatcher;
import dev.pulsermm.agenthub.infrastructure.desktop.DesktopSessionRegistry;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Hidden
@RestController
@RequestMapping("/internal/desktop-sessions")
public class DesktopSessionInternalController {

    private final DesktopSessionRegistry sessionRegistry;
    private final DesktopSessionDispatcher dispatcher;

    public DesktopSessionInternalController(DesktopSessionRegistry sessionRegistry,
                                            DesktopSessionDispatcher dispatcher) {
        this.sessionRegistry = sessionRegistry;
        this.dispatcher = dispatcher;
    }

    @PostMapping("/start")
    public ResponseEntity<Void> start(@RequestBody StartRequest req) {
        String sessionId = req.sessionId().toString();
        sessionRegistry.register(sessionId, req.endpointId(), null);
        dispatcher.startSession(req.endpointId(), sessionId, req.turnUrls(), req.turnSecret());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/end")
    public ResponseEntity<Void> end(@RequestBody EndRequest req) {
        String sessionId = req.sessionId().toString();
        sessionRegistry.get(sessionId).ifPresent(info ->
            dispatcher.endSession(info.endpointId(), sessionId)
        );
        sessionRegistry.remove(sessionId);
        return ResponseEntity.noContent().build();
    }

    record StartRequest(UUID endpointId, UUID sessionId, List<String> turnUrls, String turnSecret) {}
    record EndRequest(UUID endpointId, UUID sessionId) {}
}
