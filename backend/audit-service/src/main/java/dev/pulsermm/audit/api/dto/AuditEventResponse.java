package dev.pulsermm.audit.api.dto;

import dev.pulsermm.audit.domain.AuditEvent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "An immutable audit log entry")
public record AuditEventResponse(
    @Schema(description = "Audit event ID") UUID id,
    @Schema(description = "ID of the user who performed the action") UUID userId,
    @Schema(description = "Username of the user who performed the action") String username,
    @Schema(description = "Permission that was exercised") String permissionUsed,
    @Schema(description = "Human-readable description of what happened") String action,
    @Schema(description = "Affected endpoint ID, null for non-endpoint actions") UUID endpointId,
    @Schema(description = "Additional structured context for the action") Object payload,
    @Schema(description = "When the action occurred") Instant createdAt
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
