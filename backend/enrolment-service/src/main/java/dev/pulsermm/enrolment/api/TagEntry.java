package dev.pulsermm.enrolment.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record TagEntry(
    @Schema(description = "Tag key", example = "role")
    String key,
    @Schema(description = "Tag value", example = "db")
    String value
) {}
