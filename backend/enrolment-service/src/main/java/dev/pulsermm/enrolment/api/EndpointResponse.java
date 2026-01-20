package dev.pulsermm.enrolment.api;

import java.util.UUID;

public record EndpointResponse(
    UUID id,
    String hostname,
    String os,
    String arch,
    UUID groupId,
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
