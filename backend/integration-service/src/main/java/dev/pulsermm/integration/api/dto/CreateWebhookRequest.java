package dev.pulsermm.integration.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Request body for creating a webhook")
public record CreateWebhookRequest(
    @Schema(description = "HTTPS URL to deliver events to", example = "https://example.com/hook")
    @NotBlank(message = "url is required")
    String url,

    @Schema(description = "Event types to subscribe to, e.g. alert.fired, alert.cleared")
    @NotEmpty(message = "at least one event type is required")
    List<String> eventTypes,

    @Schema(description = "Signing secret used in HMAC-SHA256 webhook signatures (min 16 chars)")
    @NotBlank(message = "secret is required")
    @Size(min = 16, message = "secret must be at least 16 characters")
    String secret
) {}
