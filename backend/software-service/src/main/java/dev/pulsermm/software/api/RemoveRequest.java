package dev.pulsermm.software.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record RemoveRequest(
    @Schema(description = "Software name", example = "Google Chrome")
    String name
) {}
