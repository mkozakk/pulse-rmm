package dev.pulsermm.alert.api.dto;

import dev.pulsermm.alert.domain.AlertEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AlertEventResponse(
    UUID id,
    UUID ruleId,
    String ruleName,
    UUID endpointId,
    OffsetDateTime triggeredAt,
    OffsetDateTime ackedAt,
    OffsetDateTime clearedAt
) {
    public static AlertEventResponse from(AlertEvent event) {
        return new AlertEventResponse(
            event.getId(),
            event.getRule().getId(),
            event.getRule().getName(),
            event.getEndpointId(),
            event.getTriggeredAt(),
            event.getAckedAt(),
            event.getClearedAt()
        );
    }
}
