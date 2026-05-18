package dev.pulsermm.commands.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record InitiateScriptRunResponse(
        @Schema(description = "Run id")
        UUID runId
) {
}
