package dev.pulsermm.agentupdate.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

@Schema(description = "Update result reported by the agent after applying or failing an update")
public record UpdateReportRequest(
        @Schema(description = "Endpoint reporting the result") UUID endpointId,
        @Schema(description = "Version the agent attempted to install") @NotBlank String version,
        @Schema(description = "Result status: success or failed") @NotBlank String status,
        @Schema(description = "Error reason if status is failed") String reason
) {}
