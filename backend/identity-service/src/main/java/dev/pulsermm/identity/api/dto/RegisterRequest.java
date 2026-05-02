package dev.pulsermm.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @Schema(description = "Username", example = "admin")
    @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{3,64}$") String username,
    @Schema(description = "Password", minLength = 12, maxLength = 72, example = "correct horse battery staple")
    @NotNull @Size(min = 12, max = 72) String password
) {}
