package dev.pulsermm.alert.api.controller;

import dev.pulsermm.alert.api.dto.AlertEventResponse;
import dev.pulsermm.alert.application.AlertEventService;
import dev.pulsermm.alert.application.SseBroadcaster;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@Tag(name = "Alert Events", description = "Query and acknowledge fired alert events")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/alerts")
public class AlertEventController {

    private final AlertEventService alertEventService;
    private final SseBroadcaster sseBroadcaster;

    public AlertEventController(AlertEventService alertEventService, SseBroadcaster sseBroadcaster) {
        this.alertEventService = alertEventService;
        this.sseBroadcaster = sseBroadcaster;
    }

    @Operation(summary = "List alert events", description = "Returns alert events filtered by status. Use status=all to include acknowledged and cleared events.")
    @ApiResponse(responseCode = "200", description = "Alert events returned")
    @GetMapping
    public ResponseEntity<List<AlertEventResponse>> listAlerts(
            @RequestParam(defaultValue = "open") String status) {
        var events = "all".equals(status)
                ? alertEventService.listAll()
                : alertEventService.listOpen();
        return ResponseEntity.ok(events.stream().map(AlertEventResponse::from).toList());
    }

    @Operation(summary = "Acknowledge an alert", description = "Marks the alert event as acknowledged by the current user.")
    @ApiResponse(responseCode = "204", description = "Acknowledged")
    @ApiResponse(responseCode = "404", description = "Alert event not found")
    @PostMapping("/{id}/ack")
    public ResponseEntity<Void> ack(@PathVariable UUID id, Authentication authentication) {
        var userId = UUID.fromString(authentication.getName());
        alertEventService.ack(id, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Stream alert events via SSE", description = "Opens a Server-Sent Events stream that pushes new alert events in real time.")
    @ApiResponse(responseCode = "200", description = "SSE stream opened")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return sseBroadcaster.register();
    }
}
