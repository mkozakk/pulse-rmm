package dev.pulsermm.remote.api.dto;

import java.util.UUID;

public record SessionStatusResponse(UUID sessionId, String status) {}
