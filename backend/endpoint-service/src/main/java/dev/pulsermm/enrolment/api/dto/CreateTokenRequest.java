package dev.pulsermm.enrolment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record CreateTokenRequest(
    @Schema(description = "Group id the token enrols into")
    @NotNull UUID groupId,
    @Schema(description = "Token TTL in hours", example = "24", minimum = "1")
    @Positive int ttlHours
) {}
