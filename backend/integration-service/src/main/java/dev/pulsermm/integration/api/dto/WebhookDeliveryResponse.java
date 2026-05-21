package dev.pulsermm.integration.api.dto;

import dev.pulsermm.integration.domain.WebhookDelivery;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "A webhook delivery attempt with full payload detail")
public record WebhookDeliveryResponse(
    @Schema(description = "Delivery ID") UUID id,
    @Schema(description = "Webhook this delivery belongs to") UUID webhookId,
    @Schema(description = "Event type that triggered the delivery") String eventType,
    @Schema(description = "ID of the source event") UUID eventId,
    @Schema(description = "Full event payload sent to the webhook URL") Map<String, Object> payload,
    @Schema(description = "Delivery status: pending, delivered, failed, dead_letter") String status,
    @Schema(description = "Number of delivery attempts made") int attempts,
    @Schema(description = "HTTP status code from the last attempt, null if not yet attempted") Integer lastStatusCode,
    @Schema(description = "Error message from the last failed attempt") String lastError,
    @Schema(description = "When the delivery was first attempted") Instant createdAt,
    @Schema(description = "When the delivery was completed (delivered or dead-lettered)") Instant completedAt
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
