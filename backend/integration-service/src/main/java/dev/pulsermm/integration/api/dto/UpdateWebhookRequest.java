package dev.pulsermm.integration.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Request body for updating a webhook — all fields are optional")
public record UpdateWebhookRequest(
    @Schema(description = "New delivery URL, leave null to keep existing") String url,
    @Schema(description = "Replaced event type list, leave null to keep existing") List<String> eventTypes,
    @Schema(description = "Enable or disable the webhook") Boolean enabled,
    @Schema(description = "New signing secret (min 16 chars), leave null to keep existing")
    @Size(min = 16, message = "secret must be at least 16 characters")
    String secret
) {}
