package dev.pulsermm.integration.api.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateWebhookRequest(
    String url,
    List<String> eventTypes,
    Boolean enabled,
    @Size(min = 16, message = "secret must be at least 16 characters")
    String secret
) {}
