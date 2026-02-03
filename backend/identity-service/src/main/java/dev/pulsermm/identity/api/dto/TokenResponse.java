package dev.pulsermm.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
    @Schema(description = "JWT access token")
    String accessToken,
    @Schema(description = "Token lifetime in seconds", example = "900")
    long expiresIn
) {}
