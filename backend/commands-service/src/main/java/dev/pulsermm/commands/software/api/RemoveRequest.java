package dev.pulsermm.commands.software.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record RemoveRequest(
    @Schema(description = "Software name", example = "Google Chrome")
    String name,
    @Schema(description = "Software App ID", nullable = true, example = "Google.Chrome")
    String appId
) {}
