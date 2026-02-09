package dev.pulsermm.script.api;

import java.util.List;
import java.util.UUID;

public record ScriptRunResponse(
        UUID runId,
        List<ScriptRunResultResponse> results,
        long total,
        long pending
) {
}
