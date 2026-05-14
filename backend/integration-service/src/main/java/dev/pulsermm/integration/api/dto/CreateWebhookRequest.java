package dev.pulsermm.integration.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateWebhookRequest(
    @NotBlank(message = "url is required")
    String url,

    @NotEmpty(message = "at least one event type is required")
    List<String> eventTypes,

    @NotBlank(message = "secret is required")
    @Size(min = 16, message = "secret must be at least 16 characters")
    String secret
) {}
