package dev.pulsermm.integration.api.dto;

import dev.pulsermm.integration.domain.WebhookDelivery;

import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryView(
    UUID id,
    UUID webhookId,
    String eventType,
    UUID eventId,
    String payloadPreview,
    String status,
    int attempts,
    Integer lastStatusCode,
    String lastError,
    Instant createdAt,
    Instant completedAt
) {
    public static WebhookDeliveryView from(WebhookDelivery d, String payloadJson) {
        String preview = payloadJson.length() > 200 ? payloadJson.substring(0, 200) + "..." : payloadJson;
        return new WebhookDeliveryView(
            d.getId(), d.getWebhook().getId(), d.getEventType(), d.getEventId(),
            preview, d.getStatus(), d.getAttempts(),
            d.getLastStatusCode(), d.getLastError(),
            d.getCreatedAt(), d.getCompletedAt()
        );
    }
}
