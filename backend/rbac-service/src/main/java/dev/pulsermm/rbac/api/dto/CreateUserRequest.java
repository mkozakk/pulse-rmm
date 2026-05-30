package dev.pulsermm.rbac.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
    @NotBlank String username,
    String email,
    String firstName,
    String lastName,
    @NotBlank String password,
    String roleName
) {}
