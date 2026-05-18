package dev.pulsermm.commands.software.api;

import dev.pulsermm.commands.software.application.SoftwareService;
import dev.pulsermm.commands.software.domain.SoftwareCommand;
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
        var logger = org.slf4j.LoggerFactory.getLogger(SoftwareController.class);
        logger.info("GET /api/endpoints/{}/software - auth={}", endpointId, auth != null && auth.isAuthenticated());

        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var items = softwareService.getSoftwareList(endpointId);
        logger.info("getSoftwareList returned {} items for endpoint={}", items.size(), endpointId);
        var response = items.stream().map(SoftwareItemResponse::from).toList();

        // Check for hello in response
        var helloItems = response.stream().filter(r -> r.name().contains("hello")).toList();
        logger.info("Items containing 'hello': {}", helloItems.size());
        if (!helloItems.isEmpty()) {
            logger.info("Found hello: {}", helloItems.get(0).name());
        }

        if (response.size() > 0) {
            logger.info("First 3 items: {} / {} / {}",
                response.get(0).name(),
                response.size() > 1 ? response.get(1).name() : "N/A",
                response.size() > 2 ? response.get(2).name() : "N/A");
        }
        return ResponseEntity.ok(response);
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
        var cmd = softwareService.createCommand(endpointId, "install", request.name(), request.appId(), request.version());
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
        var cmd = softwareService.createCommand(endpointId, "update", request.name(), request.appId(), request.version());
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
        var cmd = softwareService.createCommand(endpointId, "remove", request.name(), request.appId(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CommandResponse(cmd.id(), cmd.status()));
    }

    public record CommandResponse(
        @Schema(description = "Command id")
        UUID id,
        @Schema(description = "Command status", example = "PENDING")
        String status
    ) {}
}
