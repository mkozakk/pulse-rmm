package dev.pulsermm.integration.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.integration.api.dto.WebhookDeliveryResponse;
import dev.pulsermm.integration.api.dto.WebhookDeliveryView;
import dev.pulsermm.integration.application.WebhookService;
import dev.pulsermm.integration.infrastructure.persistence.WebhookDeliveryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Tag(name = "Webhook Deliveries", description = "Delivery history and dead-letter queue for webhook calls")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/webhooks")
public class WebhookDeliveryController {

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    public WebhookDeliveryController(WebhookDeliveryRepository deliveryRepository,
                                      WebhookService webhookService,
                                      ObjectMapper objectMapper) {
        this.deliveryRepository = deliveryRepository;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "List deliveries for a webhook", description = "Returns delivery attempts for the given webhook, optionally filtered by status.")
    @ApiResponse(responseCode = "200", description = "Deliveries returned")
    @ApiResponse(responseCode = "404", description = "Webhook not found")
    @GetMapping("/{webhookId}/deliveries")
    @Transactional(readOnly = true)
    public List<WebhookDeliveryView> listDeliveries(
            @PathVariable UUID webhookId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        webhookService.get(webhookId);
        var pageable = PageRequest.of(0, Math.min(limit, 200));
        var page = status != null
            ? deliveryRepository.findByWebhookIdAndStatusOrderByCreatedAtDesc(webhookId, status, pageable)
            : deliveryRepository.findByWebhookIdOrderByCreatedAtDesc(webhookId, pageable);
        return page.getContent().stream()
            .map(d -> WebhookDeliveryView.from(d, toJson(d.getPayload())))
            .toList();
    }

    @Operation(summary = "List dead-letter deliveries", description = "Returns webhook deliveries that exhausted all retry attempts.")
    @ApiResponse(responseCode = "200", description = "Dead-letter deliveries returned")
    @GetMapping("/deliveries/dead-letter")
    @Transactional(readOnly = true)
    public List<WebhookDeliveryView> listDeadLetter(
            @RequestParam(defaultValue = "100") int limit) {
        var pageable = PageRequest.of(0, Math.min(limit, 500));
        return deliveryRepository.findByStatusOrderByCreatedAtDesc("dead_letter", pageable)
            .getContent().stream()
            .map(d -> WebhookDeliveryView.from(d, toJson(d.getPayload())))
            .toList();
    }

    @Operation(summary = "Get a single delivery by ID")
    @ApiResponse(responseCode = "200", description = "Delivery returned")
    @ApiResponse(responseCode = "404", description = "Delivery not found")
    @GetMapping("/deliveries/{deliveryId}")
    public WebhookDeliveryResponse getDelivery(@PathVariable UUID deliveryId) {
        var delivery = deliveryRepository.findByIdWithWebhook(deliveryId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "delivery not found"));
        return WebhookDeliveryResponse.from(delivery);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
