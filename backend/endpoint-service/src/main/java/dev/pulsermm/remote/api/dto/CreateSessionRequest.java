package dev.pulsermm.remote.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Request to create a remote desktop session")
public record CreateSessionRequest(
    @Schema(description = "Endpoint to connect to") @NotNull UUID endpointId,
    @Schema(description = "Session type: view or control") @NotNull String type
) {}
