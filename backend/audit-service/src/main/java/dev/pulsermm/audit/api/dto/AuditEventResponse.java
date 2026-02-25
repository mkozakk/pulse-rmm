package dev.pulsermm.audit.api.dto;

import dev.pulsermm.audit.domain.AuditEvent;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
    UUID id,
    UUID userId,
    String username,
    String permissionUsed,
    String action,
    UUID endpointId,
    Object payload,
    Instant createdAt
) {
    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
            event.getId(),
            event.getUserId(),
            event.getUsername(),
            event.getPermissionUsed(),
            event.getAction(),
            event.getEndpointId(),
            event.getPayload(),
            event.getCreatedAt()
        );
    }
}
