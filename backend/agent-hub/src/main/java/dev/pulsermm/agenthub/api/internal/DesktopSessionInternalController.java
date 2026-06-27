package dev.pulsermm.agenthub.api.internal;

import dev.pulsermm.agenthub.infrastructure.desktop.DesktopSessionDispatcher;
import dev.pulsermm.agenthub.infrastructure.desktop.DesktopSessionRegistry;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Hidden
@RestController
@RequestMapping("/internal/desktop-sessions")
public class DesktopSessionInternalController {

    private final DesktopSessionRegistry sessionRegistry;
    private final DesktopSessionDispatcher dispatcher;
    private final String internalSecret;

    public DesktopSessionInternalController(DesktopSessionRegistry sessionRegistry,
                                            DesktopSessionDispatcher dispatcher,
                                            @Value("${pulse.identity.internal-secret}") String internalSecret) {
        this.sessionRegistry = sessionRegistry;
        this.dispatcher = dispatcher;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/start")
    public ResponseEntity<Void> start(@RequestBody StartRequest req,
                                      @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!internalSecret.equals(token)) {
            return ResponseEntity.status(403).build();
        }
        String sessionId = req.sessionId().toString();
        sessionRegistry.register(sessionId, req.endpointId(), null);
        dispatcher.startSession(req.endpointId(), sessionId, req.turnUrls(), req.turnSecret());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/end")
    public ResponseEntity<Void> end(@RequestBody EndRequest req,
                                    @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!internalSecret.equals(token)) {
            return ResponseEntity.status(403).build();
        }
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
