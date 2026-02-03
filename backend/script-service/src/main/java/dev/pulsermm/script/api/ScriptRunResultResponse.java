package dev.pulsermm.script.api;

import dev.pulsermm.script.domain.ScriptRunResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ScriptRunResultResponse(
        @Schema(description = "Endpoint id")
        UUID endpointId,
        @Schema(description = "Exit code", nullable = true, example = "0")
        Integer exitCode,
        @Schema(description = "Captured output", nullable = true)
        String output,
        @Schema(description = "Ack timestamp", nullable = true)
        OffsetDateTime ackedAt,
        @Schema(description = "Pending flag", example = "true")
        Boolean pending
) {
    public static ScriptRunResultResponse from(ScriptRunResult result) {
        return new ScriptRunResultResponse(
                result.getEndpointId(),
                result.getExitCode(),
                result.getOutput(),
                result.getAckedAt(),
                result.isPending()
        );
    }
}
