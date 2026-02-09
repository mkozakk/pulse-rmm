package dev.pulsermm.script.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.pulsermm.script.domain.Script;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScriptResponse(
        UUID id,
        String name,
        String body,
        Boolean approved,
        OffsetDateTime approvedAt,
        UUID createdBy,
        OffsetDateTime createdAt
) {
    public static ScriptResponse from(Script script) {
        return new ScriptResponse(
                script.getId(),
                script.getName(),
                script.getBody(),
                script.isApproved(),
                script.getApprovedAt(),
                script.getCreatedBy(),
                script.getCreatedAt()
        );
    }
}
