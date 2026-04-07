package dev.pulsermm.integration.api.controller;

import dev.pulsermm.integration.api.dto.*;
import dev.pulsermm.integration.application.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "Webhooks", description = "Configure outbound webhook endpoints for alert and event delivery")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @Operation(summary = "Create a webhook")
    @ApiResponse(responseCode = "201", description = "Webhook created")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @PostMapping
    public ResponseEntity<WebhookResponse> create(@RequestBody @Valid CreateWebhookRequest req,
                                                   Authentication auth,
                                                   @RequestHeader(value = "X-User-Org-Id", required = false) UUID orgId) {
        var createdBy = UUID.fromString(auth.getName());
        var webhook = webhookService.create(req.url(), req.eventTypes(), req.secret(), createdBy, orgId);
        return ResponseEntity.created(URI.create("/api/webhooks/" + webhook.getId()))
            .body(WebhookResponse.from(webhook));
    }

    @Operation(summary = "List all webhooks")
    @ApiResponse(responseCode = "200", description = "Webhooks returned")
    @GetMapping
    public List<WebhookResponse> list(@RequestHeader(value = "X-User-Org-Id", required = false) UUID orgId) {
        return webhookService.list(orgId).stream().map(WebhookResponse::from).toList();
    }

    @Operation(summary = "Get a webhook by ID")
    @ApiResponse(responseCode = "200", description = "Webhook returned")
    @ApiResponse(responseCode = "404", description = "Webhook not found")
    @GetMapping("/{id}")
    public WebhookResponse get(@PathVariable UUID id,
                                @RequestHeader(value = "X-User-Org-Id", required = false) UUID orgId) {
        return WebhookResponse.from(webhookService.get(id, orgId));
    }

    @Operation(summary = "Update a webhook")
    @ApiResponse(responseCode = "200", description = "Webhook updated")
    @ApiResponse(responseCode = "404", description = "Webhook not found")
    @PutMapping("/{id}")
    public WebhookResponse update(@PathVariable UUID id,
                                   @RequestBody @Valid UpdateWebhookRequest req,
                                   @RequestHeader(value = "X-User-Org-Id", required = false) UUID orgId) {
        return WebhookResponse.from(
            webhookService.update(id, req.url(), req.eventTypes(), req.enabled(), req.secret(), orgId)
        );
    }

    @Operation(summary = "Delete a webhook")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "Webhook not found")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id,
                       @RequestHeader(value = "X-User-Org-Id", required = false) UUID orgId) {
        webhookService.delete(id, orgId);
    }
}
