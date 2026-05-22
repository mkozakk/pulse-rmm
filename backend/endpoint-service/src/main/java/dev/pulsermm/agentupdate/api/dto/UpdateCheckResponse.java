package dev.pulsermm.agentupdate.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of an agent update check")
public record UpdateCheckResponse(
        @Schema(description = "True if the agent is already on the current version") boolean upToDate,
        @Schema(description = "Available version string, null if up to date") String version,
        @Schema(description = "Download URL for the new binary, null if up to date") String downloadUrl,
        @Schema(description = "SHA-256 checksum of the new binary, null if up to date") String sha256,
        @Schema(description = "Size of the new binary in bytes, null if up to date") Long sizeBytes
) {
    public static UpdateCheckResponse current() {
        return new UpdateCheckResponse(true, null, null, null, null);
    }

    public static UpdateCheckResponse available(String version, String downloadUrl, String sha256, long sizeBytes) {
        return new UpdateCheckResponse(false, version, downloadUrl, sha256, sizeBytes);
    }
}
