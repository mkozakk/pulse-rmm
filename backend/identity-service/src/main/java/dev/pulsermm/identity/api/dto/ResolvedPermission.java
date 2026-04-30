package dev.pulsermm.identity.api.dto;

import java.util.UUID;

public record ResolvedPermission(String name, UUID groupScopeId) {}
