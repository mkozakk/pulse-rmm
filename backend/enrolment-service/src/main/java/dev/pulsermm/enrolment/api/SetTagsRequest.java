package dev.pulsermm.enrolment.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SetTagsRequest(
    @Schema(description = "Full tag set to apply (replaces existing)")
    List<TagEntry> tags
) {}
