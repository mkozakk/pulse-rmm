package dev.pulsermm.commands.processes.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.commands.processes.api.dto.AckRequest;
import dev.pulsermm.commands.processes.api.dto.ProcessSnapshotResponse;
import dev.pulsermm.commands.processes.api.dto.RefreshResponse;
import dev.pulsermm.commands.processes.application.ProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Processes", description = "Endpoint task manager: list and kill processes")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class ProcessController {

    private final ProcessService processService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProcessController(ProcessService processService) {
        this.processService = processService;
    }

    @Operation(summary = "Request a fresh process list from an endpoint")
    @PostMapping("/api/endpoints/{endpointId}/processes/refresh")
    public ResponseEntity<RefreshResponse> refresh(@PathVariable UUID endpointId,
                                                   Authentication auth) {
        var userId = UUID.fromString(auth.getName());
        UUID commandId = processService.refresh(endpointId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new RefreshResponse(commandId));
    }

    @Operation(summary = "Get the latest completed process snapshot for an endpoint")
    @GetMapping("/api/endpoints/{endpointId}/processes/latest")
    public ResponseEntity<ProcessSnapshotResponse> latest(@PathVariable UUID endpointId) {
        return processService.latestCompleted(endpointId)
            .map(s -> {
                try {
                    var procsNode = s.getProcesses() == null
                        ? null
                        : mapper.readTree(s.getProcesses());
                    return ResponseEntity.ok(new ProcessSnapshotResponse(
                        s.getId(), s.getEndpointId(), s.getStatus(),
                        procsNode, s.getCompletedAt()));
                } catch (Exception e) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .<ProcessSnapshotResponse>build();
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Kill a process on the endpoint by PID")
    @PostMapping("/api/endpoints/{endpointId}/processes/{pid}/kill")
    public ResponseEntity<RefreshResponse> kill(@PathVariable UUID endpointId,
                                                @PathVariable int pid,
                                                Authentication auth) {
        var userId = UUID.fromString(auth.getName());
        UUID commandId = processService.kill(endpointId, pid, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new RefreshResponse(commandId));
    }

    @Operation(summary = "Internal: gateway ack callback for list-processes")
    @PostMapping("/api/processes/commands/{commandId}/ack")
    public ResponseEntity<Void> ackList(@PathVariable UUID commandId,
                                        @RequestBody AckRequest req) {
        processService.ackListProcesses(commandId, req.exitCode(), req.output());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Internal: gateway ack callback for kill-process")
    @PostMapping("/api/processes/kill-commands/{commandId}/ack")
    public ResponseEntity<Void> ackKill(@PathVariable UUID commandId,
                                        @RequestBody AckRequest req) {
        processService.ackKillProcess(commandId, req.exitCode(), req.output());
        return ResponseEntity.noContent().build();
    }
}
