package dev.pulsermm.software.api;

import dev.pulsermm.software.domain.SoftwareItem;
import io.swagger.v3.oas.annotations.media.Schema;

public record SoftwareItemResponse(
    @Schema(description = "Software name", example = "Google Chrome")
    String name,
    @Schema(description = "Software App ID", example = "Google.Chrome")
    String appId,
    @Schema(description = "Installed version", example = "123.0.0")
    String version,
    @Schema(description = "Available update version", example = "124.0.0")
    String updateTo,
    @Schema(description = "Is it from MS Store", example = "false")
    Boolean isStore,
    @Schema(description = "Source", example = "winget")
    String source
) {
    public static SoftwareItemResponse from(SoftwareItem item) {
        return new SoftwareItemResponse(item.name(), item.appId(), item.version(), item.updateTo(), item.isStore(), item.source());
    }
}
