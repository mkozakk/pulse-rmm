package dev.pulsermm.script.api;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public record RunScriptRequest(
        @NotEmpty(message = "At least one endpoint is required")
        List<String> endpointIds,

        Map<String, String> secrets
) {
}
