package dev.pulsermm.remote.api;

import dev.pulsermm.remote.api.dto.CreateSessionRequest;
import dev.pulsermm.remote.api.dto.CreateSessionResponse;
import dev.pulsermm.remote.api.dto.SessionStatusResponse;
import dev.pulsermm.remote.application.SessionService;
import dev.pulsermm.remote.domain.DesktopSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<CreateSessionResponse> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            Authentication auth) {
        UUID technicianId = UUID.fromString(auth.getName());
        SessionService.SessionResult result = sessionService.createSession(request.endpointId(), technicianId);
        DesktopSession session = result.session();
        var response = new CreateSessionResponse(
            session.getId(),
            result.turnUrls(),
            session.getTurnUsername(),
            session.getTurnCredential(),
            result.canControl()
        );
        return ResponseEntity
            .created(URI.create("/api/sessions/" + session.getId()))
            .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionStatusResponse> getSession(@PathVariable UUID id) {
        DesktopSession session = sessionService.getSession(id);
        return ResponseEntity.ok(new SessionStatusResponse(session.getId(), session.getStatus()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> endSession(@PathVariable UUID id, Authentication auth) {
        UUID technicianId = UUID.fromString(auth.getName());
        sessionService.endSession(id, technicianId);
        return ResponseEntity.noContent().build();
    }
}
