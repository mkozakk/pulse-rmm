package dev.pulsermm.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record RegisterResponse(
    @Schema(description = "User id")
    UUID id,
    @Schema(description = "Username", example = "admin")
    String username
) {}
