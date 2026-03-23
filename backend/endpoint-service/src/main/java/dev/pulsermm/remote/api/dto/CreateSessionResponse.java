package dev.pulsermm.remote.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Response containing session ID and TURN credentials for WebRTC signalling")
public record CreateSessionResponse(
    @Schema(description = "Session ID") UUID sessionId,
    @Schema(description = "TURN server URLs for WebRTC ICE") List<String> turnUrls,
    @Schema(description = "TURN username") String turnUsername,
    @Schema(description = "TURN credential") String turnCredential,
    @Schema(description = "Whether the technician has control permissions for this session") boolean canControl
) {}
