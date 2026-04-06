package dev.pulsermm.rbac.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateOrganizationRequest(@NotBlank String name) {}
