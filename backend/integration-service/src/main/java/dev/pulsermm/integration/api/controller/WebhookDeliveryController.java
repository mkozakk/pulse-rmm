package dev.pulsermm.integration.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.integration.api.dto.WebhookDeliveryResponse;
import dev.pulsermm.integration.api.dto.WebhookDeliveryView;
import dev.pulsermm.integration.application.WebhookService;
import dev.pulsermm.integration.infrastructure.persistence.WebhookDeliveryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

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
