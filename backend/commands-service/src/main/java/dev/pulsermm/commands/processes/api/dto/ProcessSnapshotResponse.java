package dev.pulsermm.commands.processes.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProcessSnapshotResponse(
    UUID id,
    UUID endpointId,
    String status,
    JsonNode processes,
    OffsetDateTime completedAt
) {}
