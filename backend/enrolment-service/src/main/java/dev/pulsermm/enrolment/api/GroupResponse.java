package dev.pulsermm.enrolment.api;

import java.util.UUID;

public record GroupResponse(UUID id, String name, UUID parentId) {}
