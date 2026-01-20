package dev.pulsermm.enrolment.api;

import java.time.Instant;
import java.util.UUID;

public record TokenResponse(
    UUID id,
    Instant expiresAt
) {}
