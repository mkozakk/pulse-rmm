package dev.pulsermm.alert.api.dto;

import dev.pulsermm.alert.domain.AlertEvent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "A fired alert event")
public record AlertEventResponse(
    @Schema(description = "Alert event ID") UUID id,
    @Schema(description = "ID of the rule that triggered this event") UUID ruleId,
    @Schema(description = "Name of the rule that triggered this event") String ruleName,
    @Schema(description = "Endpoint that violated the rule") UUID endpointId,
    @Schema(description = "When the alert fired") OffsetDateTime triggeredAt,
    @Schema(description = "When the alert was acknowledged, null if still open") OffsetDateTime ackedAt,
    @Schema(description = "When the alert cleared, null if still active") OffsetDateTime clearedAt
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
