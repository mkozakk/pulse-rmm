package dev.pulsermm.agentupdate.api.dto;

import dev.pulsermm.agentupdate.domain.AgentVersion;

import java.time.Instant;
import java.util.UUID;

public record AgentVersionResponse(
        UUID id,
        String version,
        String os,
        String arch,
        String sha256,
        long sizeBytes,
        boolean current,
        Instant publishedAt
) {
    public static AgentVersionResponse from(AgentVersion v) {
        return new AgentVersionResponse(
                v.getId(), v.getVersion(), v.getOs(), v.getArch(),
                v.getSha256(), v.getSizeBytes(), v.isCurrent(), v.getPublishedAt());
    }
}
