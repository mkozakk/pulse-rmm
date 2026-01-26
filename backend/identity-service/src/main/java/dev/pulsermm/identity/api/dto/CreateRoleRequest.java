package dev.pulsermm.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
    @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_ ]{1,100}$") @Size(min = 1, max = 100) String name
) {}
