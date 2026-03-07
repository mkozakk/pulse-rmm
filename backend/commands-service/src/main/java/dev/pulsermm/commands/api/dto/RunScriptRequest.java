package dev.pulsermm.commands.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public record RunScriptRequest(
        @Schema(description = "Endpoint ids to run against", example = "[\"b0f43c2c-1f5a-4b42-9b79-0a8a9f5a2ef1\"]")
        @NotEmpty(message = "At least one endpoint is required")
        List<String> endpointIds,

        @Schema(description = "Secrets to inject", nullable = true)
        Map<String, String> secrets
) {
}
