package dev.pulsermm.rbac.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationResponse(UUID id, String name, OffsetDateTime createdAt) {}
