package dev.pulsermm.alert.api.controller;

import dev.pulsermm.alert.api.dto.AlertRuleResponse;
import dev.pulsermm.alert.api.dto.CreateAlertRuleRequest;
import dev.pulsermm.alert.application.AlertRuleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alert-rules")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    public AlertRuleController(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @PostMapping
    public ResponseEntity<AlertRuleResponse> createRule(
            @Valid @RequestBody CreateAlertRuleRequest request,
            Authentication authentication) {
        var userId = UUID.fromString(authentication.getName());
        var rule = alertRuleService.create(request, userId);
        var response = AlertRuleResponse.from(rule);
        return ResponseEntity.created(URI.create("/api/alert-rules/" + rule.getId())).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AlertRuleResponse>> listRules() {
        var rules = alertRuleService.list().stream()
                .map(AlertRuleResponse::from)
                .toList();
        return ResponseEntity.ok(rules);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        alertRuleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
