package dev.pulsermm.agentupdate.api.dto;

public record UpdateCheckResponse(
        boolean upToDate,
        String version,
        String downloadUrl,
        String sha256,
        Long sizeBytes
) {
    public static UpdateCheckResponse current() {
        return new UpdateCheckResponse(true, null, null, null, null);
    }

    public static UpdateCheckResponse available(String version, String downloadUrl, String sha256, long sizeBytes) {
        return new UpdateCheckResponse(false, version, downloadUrl, sha256, sizeBytes);
    }
}
