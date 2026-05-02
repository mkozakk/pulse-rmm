package dev.pulsermm.enrolment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record MoveEndpointRequest(
    @Schema(description = "Target group id")
    UUID groupId
) {}
