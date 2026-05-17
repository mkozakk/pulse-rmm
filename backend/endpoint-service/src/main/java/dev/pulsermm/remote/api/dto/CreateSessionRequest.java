package dev.pulsermm.remote.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateSessionRequest(
    @NotNull UUID endpointId,
    @NotNull String type
) {}
