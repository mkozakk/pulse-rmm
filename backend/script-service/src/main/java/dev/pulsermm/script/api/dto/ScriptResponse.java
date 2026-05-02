package dev.pulsermm.script.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.pulsermm.script.domain.Script;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScriptResponse(
        @Schema(description = "Script id")
        UUID id,
        @Schema(description = "Script name", example = "Restart service")
        String name,
        @Schema(description = "Script body", nullable = true)
        String body,
        @Schema(description = "Approved flag", example = "false")
        Boolean approved,
        @Schema(description = "Approval timestamp", nullable = true)
        OffsetDateTime approvedAt,
        @Schema(description = "User id who created the script")
        UUID createdBy,
        @Schema(description = "Created timestamp")
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
