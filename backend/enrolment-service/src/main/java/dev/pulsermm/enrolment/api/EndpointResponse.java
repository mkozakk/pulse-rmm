package dev.pulsermm.enrolment.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record EndpointResponse(
    @Schema(description = "Endpoint id")
    UUID id,
    @Schema(description = "Endpoint hostname", example = "DESKTOP-123")
    String hostname,
    @Schema(description = "Operating system", example = "windows")
    String os,
    @Schema(description = "CPU architecture", example = "amd64")
    String arch,
    @Schema(description = "Group id")
    UUID groupId,
    @Schema(description = "Online status", example = "online")
    String status
) {
    public static EndpointResponse from(dev.pulsermm.enrolment.domain.Endpoint endpoint) {
        String status = endpoint.getLastSeen().isAfter(java.time.Instant.now().minusSeconds(90))
            ? "online"
            : "offline";
        return new EndpointResponse(
            endpoint.getId(),
            endpoint.getHostname(),
            endpoint.getOs(),
            endpoint.getArch(),
            endpoint.getGroupId(),
            status
        );
    }
}
