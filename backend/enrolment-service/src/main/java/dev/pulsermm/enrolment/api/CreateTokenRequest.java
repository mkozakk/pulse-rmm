package dev.pulsermm.enrolment.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record CreateTokenRequest(
    @NotNull UUID groupId,
    @Positive int ttlHours
) {}
