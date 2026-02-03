package dev.pulsermm.script.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateScriptRequest(
        @Schema(description = "Script name", example = "Restart service")
        @NotBlank(message = "Script name is required")
        String name,

        @Schema(description = "Script body", example = "Write-Output 'hello'")
        @NotBlank(message = "Script body is required")
        String body
) {
}
