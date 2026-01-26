package dev.pulsermm.gateway.api;

import java.util.UUID;

public record ResolvedPermission(String name, UUID groupScopeId) {}
