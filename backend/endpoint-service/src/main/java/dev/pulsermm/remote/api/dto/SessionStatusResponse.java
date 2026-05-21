package dev.pulsermm.remote.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Current status of a remote desktop session")
public record SessionStatusResponse(
    @Schema(description = "Session ID") UUID sessionId,
    @Schema(description = "Session status: active, ended") String status
) {}
