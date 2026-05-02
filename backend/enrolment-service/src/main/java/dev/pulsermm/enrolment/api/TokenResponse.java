package dev.pulsermm.enrolment.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record TokenResponse(
    @Schema(description = "Token id (used by agent to enrol)")
    UUID id,
    @Schema(description = "Token expiry timestamp", example = "2026-01-01T00:00:00Z")
    Instant expiresAt
) {}
