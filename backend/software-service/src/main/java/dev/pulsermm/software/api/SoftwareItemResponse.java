package dev.pulsermm.software.api;

import dev.pulsermm.software.domain.SoftwareItem;
import io.swagger.v3.oas.annotations.media.Schema;

public record SoftwareItemResponse(
    @Schema(description = "Software name", example = "Google Chrome")
    String name,
    @Schema(description = "Installed version", example = "123.0.0")
    String version,
    @Schema(description = "Source", example = "winget")
    String source
) {
    public static SoftwareItemResponse from(SoftwareItem item) {
        return new SoftwareItemResponse(item.name(), item.version(), item.source());
    }
}
