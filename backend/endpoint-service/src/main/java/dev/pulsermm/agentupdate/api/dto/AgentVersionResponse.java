package dev.pulsermm.agentupdate.api.dto;

import dev.pulsermm.agentupdate.domain.AgentVersion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A published agent binary release")
public record AgentVersionResponse(
        @Schema(description = "Version ID") UUID id,
        @Schema(description = "Version string, e.g. 1.2.3") String version,
        @Schema(description = "Target OS: linux or windows") String os,
        @Schema(description = "Target architecture, e.g. amd64") String arch,
        @Schema(description = "Artifact format: tar.gz, zip, deb, rpm, exe") String artifactType,
        @Schema(description = "SHA-256 checksum of the binary") String sha256,
        @Schema(description = "Binary size in bytes") long sizeBytes,
        @Schema(description = "Whether this is the version agents should upgrade to") boolean current,
        @Schema(description = "When this version was published") Instant publishedAt
) {
    public static AgentVersionResponse from(AgentVersion v) {
        return new AgentVersionResponse(
                v.getId(), v.getVersion(), v.getOs(), v.getArch(),
                v.getArtifactType(), v.getSha256(), v.getSizeBytes(),
                v.isCurrent(), v.getPublishedAt());
    }
}
