package dev.pulsermm.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{3,64}$") String username,
    @NotNull @Size(min = 12, max = 72) String password
) {}
