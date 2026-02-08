package dev.pulsermm.script.api;

import dev.pulsermm.script.domain.ScriptRunResult;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ScriptRunResultResponse(
        UUID endpointId,
        Integer exitCode,
        String output,
        OffsetDateTime ackedAt,
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
