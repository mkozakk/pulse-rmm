package dev.pulsermm.rbac.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
    @Schema(description = "Role name", example = "Admin")
    @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_ ]{1,100}$") @Size(min = 1, max = 100) String name
) {}
