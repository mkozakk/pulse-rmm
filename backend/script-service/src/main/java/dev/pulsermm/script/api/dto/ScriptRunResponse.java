package dev.pulsermm.script.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record ScriptRunResponse(
        @Schema(description = "Run id")
        UUID runId,
        @Schema(description = "Per-endpoint results")
        List<ScriptRunResultResponse> results,
        @Schema(description = "Total endpoints in run", example = "10")
        long total,
        @Schema(description = "Pending endpoints", example = "3")
        long pending
) {
}
