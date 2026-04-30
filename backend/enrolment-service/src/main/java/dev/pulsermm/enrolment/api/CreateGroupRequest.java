package dev.pulsermm.enrolment.api;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateGroupRequest(
    @NotBlank String name,
    UUID parentId
) {}
