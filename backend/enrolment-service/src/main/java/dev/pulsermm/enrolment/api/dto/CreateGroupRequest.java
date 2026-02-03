package dev.pulsermm.enrolment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateGroupRequest(
    @Schema(description = "Group name", example = "Servers")
    @NotBlank String name,
    @Schema(description = "Parent group id", nullable = true)
    UUID parentId
) {}
