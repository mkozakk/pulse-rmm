package dev.pulsermm.script.api;

import java.util.List;

public record ListScriptsResponse(
        List<ScriptResponse> scripts,
        long total
) {
}
