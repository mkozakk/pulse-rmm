package dev.pulsermm.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @Schema(description = "Username", example = "admin")
    @NotBlank String username,
    @Schema(description = "Password", example = "correct horse battery staple")
    @NotBlank String password
) {}
