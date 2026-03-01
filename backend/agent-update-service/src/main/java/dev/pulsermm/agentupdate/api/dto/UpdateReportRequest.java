package dev.pulsermm.agentupdate.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record UpdateReportRequest(
        UUID endpointId,
        @NotBlank String version,
        @NotBlank String status,
        String reason
) {}
