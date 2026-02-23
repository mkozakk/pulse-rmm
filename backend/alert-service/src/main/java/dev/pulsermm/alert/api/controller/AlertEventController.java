package dev.pulsermm.alert.api.controller;

import dev.pulsermm.alert.api.dto.AlertEventResponse;
import dev.pulsermm.alert.application.AlertEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
public class AlertEventController {

    private final AlertEventService alertEventService;

    public AlertEventController(AlertEventService alertEventService) {
        this.alertEventService = alertEventService;
    }

    @GetMapping
    public ResponseEntity<List<AlertEventResponse>> listAlerts(
            @RequestParam(defaultValue = "open") String status) {
        var events = "all".equals(status)
                ? alertEventService.listAll()
                : alertEventService.listOpen();
        return ResponseEntity.ok(events.stream().map(AlertEventResponse::from).toList());
    }

    @PostMapping("/{id}/ack")
    public ResponseEntity<Void> ack(@PathVariable UUID id, Authentication authentication) {
        var userId = UUID.fromString(authentication.getName());
        alertEventService.ack(id, userId);
        return ResponseEntity.noContent().build();
    }
}
