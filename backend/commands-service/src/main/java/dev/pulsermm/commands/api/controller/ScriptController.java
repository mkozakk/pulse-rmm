package dev.pulsermm.commands.api.controller;

import dev.pulsermm.commands.api.dto.CommandAckRequest;
import dev.pulsermm.commands.api.dto.CreateScriptRequest;
import dev.pulsermm.commands.api.dto.CreateScriptResponse;
import dev.pulsermm.commands.api.dto.InitiateScriptRunResponse;
import dev.pulsermm.commands.api.dto.ListScriptsResponse;
import dev.pulsermm.commands.api.dto.RunScriptRequest;
import dev.pulsermm.commands.api.dto.ScriptResponse;
import dev.pulsermm.commands.api.dto.ScriptRunResponse;
import dev.pulsermm.commands.api.dto.ScriptRunResultResponse;
import dev.pulsermm.commands.application.ScriptService;
import dev.pulsermm.commands.application.ScriptService.ScriptStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Scripts", description = "Script library, approval and bulk execution")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/scripts")
public class ScriptController {

    private final ScriptService scriptService;

    public ScriptController(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    @Operation(summary = "Upload a new script")
    @ApiResponse(responseCode = "201", description = "Script created, pending approval",
        content = @Content(schema = @Schema(implementation = CreateScriptResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @PostMapping
    public ResponseEntity<CreateScriptResponse> createScript(
            @Valid @RequestBody CreateScriptRequest request,
            Authentication authentication) {
        var userId = UUID.fromString(authentication.getName());
        var script = scriptService.createScript(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateScriptResponse(script.getId()));
    }

    @Operation(summary = "List scripts",
        description = "Filter by status: all | pending | library")
    @ApiResponse(responseCode = "200", description = "Paginated script list",
        content = @Content(schema = @Schema(implementation = ListScriptsResponse.class)))
    @GetMapping
    public ResponseEntity<ListScriptsResponse> listScripts(
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var scriptStatus = ScriptStatus.valueOf(status.toUpperCase());
        var pageable = PageRequest.of(page, size);
        var scripts = scriptService.listScripts(scriptStatus, pageable);
        var response = new ListScriptsResponse(
                scripts.getContent().stream()
                        .map(ScriptResponse::from)
                        .toList(),
                scripts.getTotalElements()
        );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get a script by ID")
    @ApiResponse(responseCode = "200", description = "Script details",
        content = @Content(schema = @Schema(implementation = ScriptResponse.class)))
    @ApiResponse(responseCode = "404", description = "Script not found")
    @GetMapping("/{id}")
    public ResponseEntity<ScriptResponse> getScript(@PathVariable UUID id) {
        var script = scriptService.getScriptById(id);
        return ResponseEntity.ok(ScriptResponse.from(script));
    }

    @Operation(summary = "Approve script")
    @ApiResponse(responseCode = "200", description = "Script approved",
        content = @Content(schema = @Schema(implementation = ScriptResponse.class)))
    @ApiResponse(responseCode = "404", description = "Script not found")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ScriptResponse> approveScript(@PathVariable UUID id) {
        var script = scriptService.approveScript(id);
        return ResponseEntity.ok(ScriptResponse.from(script));
    }

    @Operation(summary = "Run script",
        description = "Triggers async execution across selected endpoints")
    @ApiResponse(responseCode = "202", description = "Run initiated",
        content = @Content(schema = @Schema(implementation = InitiateScriptRunResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "404", description = "Script not found")
    @PostMapping("/{id}/run")
    public ResponseEntity<InitiateScriptRunResponse> runScript(
            @PathVariable UUID id,
            @Valid @RequestBody RunScriptRequest request,
            Authentication authentication) {
        var userId = UUID.fromString(authentication.getName());
        var runData = scriptService.runScript(id, request.endpointIds(), request.secrets(), userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new InitiateScriptRunResponse(runData.runId()));
    }

    @Operation(summary = "Get script run results")
    @ApiResponse(responseCode = "200", description = "Run results",
        content = @Content(schema = @Schema(implementation = ScriptRunResponse.class)))
    @ApiResponse(responseCode = "404", description = "Run not found")
    @GetMapping("/runs/{runId}/results")
    public ResponseEntity<ScriptRunResponse> getScriptRunResults(@PathVariable UUID runId) {
        var runData = scriptService.getScriptRunResults(runId);
        var results = runData.results().stream()
                .map(ScriptRunResultResponse::from)
                .collect(Collectors.toList());
        var response = new ScriptRunResponse(runData.runId(), results, runData.total(), runData.pending());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Acknowledge script execution")
    @ApiResponse(responseCode = "204", description = "Acknowledged")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "404", description = "Delivery not found")
    @PostMapping("/runs/{runId}/endpoints/{endpointId}/ack")
    public ResponseEntity<Void> ackScriptExecution(
            @PathVariable UUID runId,
            @PathVariable UUID endpointId,
            @Valid @RequestBody CommandAckRequest request) {
        scriptService.ackScriptExecution(runId, endpointId, request.exitCode(), request.output());
        return ResponseEntity.noContent().build();
    }
}
