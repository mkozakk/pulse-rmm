package dev.pulsermm.script.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CommandAckRequest(
        @Schema(description = "Process exit code", example = "0", nullable = true)
        Integer exitCode,
        @Schema(description = "Captured output", nullable = true)
        String output
) {
}
