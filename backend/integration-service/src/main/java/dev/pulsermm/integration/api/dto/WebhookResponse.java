package dev.pulsermm.integration.api.dto;

import dev.pulsermm.integration.domain.Webhook;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "A configured webhook endpoint")
public record WebhookResponse(
    @Schema(description = "Webhook ID") UUID id,
    @Schema(description = "Delivery URL") String url,
    @Schema(description = "Subscribed event types") List<String> eventTypes,
    @Schema(description = "Whether delivery is active") boolean enabled,
    @Schema(description = "User who created the webhook") UUID createdBy,
    @Schema(description = "Creation timestamp") Instant createdAt
) {
    public static WebhookResponse from(Webhook w) {
        return new WebhookResponse(
            w.getId(), w.getUrl(), w.getEventTypes(),
            w.isEnabled(), w.getCreatedBy(), w.getCreatedAt()
        );
    }
}
