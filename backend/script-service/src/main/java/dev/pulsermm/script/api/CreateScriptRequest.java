package dev.pulsermm.script.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateScriptRequest(
        @NotBlank(message = "Script name is required")
        String name,

        @NotBlank(message = "Script body is required")
        String body
) {
}
