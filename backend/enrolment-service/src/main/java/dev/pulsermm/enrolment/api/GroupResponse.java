package dev.pulsermm.enrolment.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record GroupResponse(
    @Schema(description = "Group id")
    UUID id,
    @Schema(description = "Group name", example = "Default")
    String name,
    @Schema(description = "Parent group id", nullable = true)
    UUID parentId
) {}
