package dev.pulsermm.remote.api;

import dev.pulsermm.remote.api.dto.CreateSessionRequest;
import dev.pulsermm.remote.api.dto.CreateSessionResponse;
import dev.pulsermm.remote.api.dto.SessionStatusResponse;
import dev.pulsermm.remote.application.SessionService;
import dev.pulsermm.remote.domain.DesktopSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Remote Sessions", description = "Create and manage remote desktop sessions on endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(summary = "Create a remote session", description = "Initiates a remote desktop session for the given endpoint. Returns TURN credentials for WebRTC signalling.")
    @ApiResponse(responseCode = "201", description = "Session created, TURN credentials returned")
    @ApiResponse(responseCode = "404", description = "Endpoint not found")
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

    @Operation(summary = "Get session status")
    @ApiResponse(responseCode = "200", description = "Session status returned")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @GetMapping("/{id}")
    public ResponseEntity<SessionStatusResponse> getSession(@PathVariable UUID id) {
        DesktopSession session = sessionService.getSession(id);
        return ResponseEntity.ok(new SessionStatusResponse(session.getId(), session.getStatus()));
    }

    @Operation(summary = "End a remote session")
    @ApiResponse(responseCode = "204", description = "Session ended")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> endSession(@PathVariable UUID id, Authentication auth) {
        UUID technicianId = UUID.fromString(auth.getName());
        sessionService.endSession(id, technicianId);
        return ResponseEntity.noContent().build();
    }
}
