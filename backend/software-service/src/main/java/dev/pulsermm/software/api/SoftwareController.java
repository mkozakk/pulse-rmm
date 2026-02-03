package dev.pulsermm.software.api;

import dev.pulsermm.software.application.SoftwareService;
import dev.pulsermm.software.domain.SoftwareCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Software", description = "Endpoint software inventory and command queue")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/endpoints/{endpointId}/software")
public class SoftwareController {

    private final SoftwareService softwareService;

    public SoftwareController(SoftwareService softwareService) {
        this.softwareService = softwareService;
    }

    @Operation(summary = "List installed software")
    @ApiResponse(responseCode = "200", description = "Software list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = SoftwareItemResponse.class))))
    @ApiResponse(responseCode = "401", description = "Unauthorized")
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

    @Operation(summary = "Install software")
    @ApiResponse(responseCode = "201", description = "Command created",
        content = @Content(schema = @Schema(implementation = CommandResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
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

    @Operation(summary = "Update software")
    @ApiResponse(responseCode = "201", description = "Command created",
        content = @Content(schema = @Schema(implementation = CommandResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
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

    @Operation(summary = "Remove software")
    @ApiResponse(responseCode = "201", description = "Command created",
        content = @Content(schema = @Schema(implementation = CommandResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
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

    public record CommandResponse(
        @Schema(description = "Command id")
        UUID id,
        @Schema(description = "Command status", example = "PENDING")
        String status
    ) {}
}
