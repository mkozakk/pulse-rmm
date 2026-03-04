package dev.pulsermm.integration.api.dto;

import dev.pulsermm.integration.domain.Webhook;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookResponse(
    UUID id,
    String url,
    List<String> eventTypes,
    boolean enabled,
    UUID createdBy,
    Instant createdAt
) {
    public static WebhookResponse from(Webhook w) {
        return new WebhookResponse(
            w.getId(), w.getUrl(), w.getEventTypes(),
            w.isEnabled(), w.getCreatedBy(), w.getCreatedAt()
        );
    }
}
