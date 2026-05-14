package dev.pulsermm.integration.api.controller;

import dev.pulsermm.integration.api.JwtAuthFilter;
import dev.pulsermm.integration.api.dto.*;
import dev.pulsermm.integration.application.WebhookService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<WebhookResponse> create(@RequestBody @Valid CreateWebhookRequest req,
                                                   Authentication auth) {
        var createdBy = JwtAuthFilter.currentUserId(auth);
        var webhook = webhookService.create(req.url(), req.eventTypes(), req.secret(), createdBy);
        return ResponseEntity.created(URI.create("/api/webhooks/" + webhook.getId()))
            .body(WebhookResponse.from(webhook));
    }

    @GetMapping
    public List<WebhookResponse> list() {
        return webhookService.list().stream().map(WebhookResponse::from).toList();
    }

    @GetMapping("/{id}")
    public WebhookResponse get(@PathVariable UUID id) {
        return WebhookResponse.from(webhookService.get(id));
    }

    @PutMapping("/{id}")
    public WebhookResponse update(@PathVariable UUID id,
                                   @RequestBody @Valid UpdateWebhookRequest req) {
        return WebhookResponse.from(
            webhookService.update(id, req.url(), req.eventTypes(), req.enabled(), req.secret())
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        webhookService.delete(id);
    }
}
