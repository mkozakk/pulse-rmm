package dev.pulsermm.integration.api.dto;

import dev.pulsermm.integration.domain.WebhookDelivery;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WebhookDeliveryResponse(
    UUID id,
    UUID webhookId,
    String eventType,
    UUID eventId,
    Map<String, Object> payload,
    String status,
    int attempts,
    Integer lastStatusCode,
    String lastError,
    Instant createdAt,
    Instant completedAt
) {
    public static WebhookDeliveryResponse from(WebhookDelivery d) {
        return new WebhookDeliveryResponse(
            d.getId(), d.getWebhook().getId(), d.getEventType(), d.getEventId(),
            d.getPayload(), d.getStatus(), d.getAttempts(),
            d.getLastStatusCode(), d.getLastError(),
            d.getCreatedAt(), d.getCompletedAt()
        );
    }
}
