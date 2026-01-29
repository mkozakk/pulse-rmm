package dev.pulsermm.software.api;

import dev.pulsermm.software.domain.SoftwareItem;

public record SoftwareItemResponse(String name, String version, String source) {
    public static SoftwareItemResponse from(SoftwareItem item) {
        return new SoftwareItemResponse(item.name(), item.version(), item.source());
    }
}
