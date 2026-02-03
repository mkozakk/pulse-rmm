package dev.pulsermm.script.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListScriptsResponse(
        @Schema(description = "Scripts")
        List<ScriptResponse> scripts,
        @Schema(description = "Total scripts matching filter", example = "42")
        long total
) {
}
