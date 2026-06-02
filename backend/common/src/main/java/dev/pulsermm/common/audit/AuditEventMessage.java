package dev.pulsermm.common.audit;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record AuditEventMessage(
    UUID userId,
    String username,
    String permissionUsed,
    String action,
    UUID endpointId,
    String payloadJson,
    Instant createdAt,
    UUID orgId
) implements Serializable {}
