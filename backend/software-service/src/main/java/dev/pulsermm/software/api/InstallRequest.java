package dev.pulsermm.software.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record InstallRequest(
    @Schema(description = "Software name", example = "Google Chrome")
    String name,
    @Schema(description = "Target version", nullable = true, example = "123.0.0")
    String version
) {}
