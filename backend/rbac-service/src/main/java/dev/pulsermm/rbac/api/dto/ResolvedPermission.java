package dev.pulsermm.rbac.api.dto;

import java.util.UUID;

public record ResolvedPermission(String name, UUID groupScopeId) {}
