package dev.pulsermm.remote.api.dto;

import java.util.List;
import java.util.UUID;

public record CreateSessionResponse(
    UUID sessionId,
    List<String> turnUrls,
    String turnUsername,
    String turnCredential,
    boolean canControl
) {}
