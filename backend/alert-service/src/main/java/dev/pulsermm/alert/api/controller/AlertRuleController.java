package dev.pulsermm.alert.api.controller;

import dev.pulsermm.alert.api.dto.AlertRuleResponse;
import dev.pulsermm.alert.api.dto.CreateAlertRuleRequest;
import dev.pulsermm.alert.application.AlertRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Alert Rules", description = "Manage metric threshold rules that trigger alert events")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/alert-rules")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    public AlertRuleController(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @Operation(summary = "Create an alert rule")
    @ApiResponse(responseCode = "201", description = "Rule created")
    @ApiResponse(responseCode = "400", description = "Invalid rule definition")
    @PostMapping
    public ResponseEntity<AlertRuleResponse> createRule(
            @Valid @RequestBody CreateAlertRuleRequest request,
            Authentication authentication) {
        var userId = UUID.fromString(authentication.getName());
        var rule = alertRuleService.create(request, userId);
        var response = AlertRuleResponse.from(rule);
        return ResponseEntity.created(URI.create("/api/alert-rules/" + rule.getId())).body(response);
    }

    @Operation(summary = "List all alert rules")
    @ApiResponse(responseCode = "200", description = "Rules returned")
    @GetMapping
    public ResponseEntity<List<AlertRuleResponse>> listRules() {
        var rules = alertRuleService.list().stream()
                .map(AlertRuleResponse::from)
                .toList();
        return ResponseEntity.ok(rules);
    }

    @Operation(summary = "Delete an alert rule")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "Rule not found")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        alertRuleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
