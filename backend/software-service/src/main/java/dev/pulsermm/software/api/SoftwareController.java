package dev.pulsermm.software.api;

import dev.pulsermm.software.application.SoftwareService;
import dev.pulsermm.software.domain.SoftwareCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/endpoints/{endpointId}/software")
public class SoftwareController {

    private final SoftwareService softwareService;

    public SoftwareController(SoftwareService softwareService) {
        this.softwareService = softwareService;
    }

    @GetMapping
    public ResponseEntity<List<SoftwareItemResponse>> list(
            @PathVariable UUID endpointId,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var items = softwareService.getSoftwareList(endpointId);
        return ResponseEntity.ok(items.stream().map(SoftwareItemResponse::from).toList());
    }

    @PostMapping("/install")
    public ResponseEntity<CommandResponse> install(
            @PathVariable UUID endpointId,
            @Valid @RequestBody InstallRequest request,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var cmd = softwareService.createCommand(endpointId, "install", request.name(), request.version());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CommandResponse(cmd.id(), cmd.status()));
    }

    @PostMapping("/update")
    public ResponseEntity<CommandResponse> update(
            @PathVariable UUID endpointId,
            @Valid @RequestBody InstallRequest request,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var cmd = softwareService.createCommand(endpointId, "update", request.name(), request.version());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CommandResponse(cmd.id(), cmd.status()));
    }

    @PostMapping("/remove")
    public ResponseEntity<CommandResponse> remove(
            @PathVariable UUID endpointId,
            @Valid @RequestBody RemoveRequest request,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var cmd = softwareService.createCommand(endpointId, "remove", request.name(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CommandResponse(cmd.id(), cmd.status()));
    }

    public record CommandResponse(UUID id, String status) {}
}
