package dev.pulsermm.common.rbac;

import java.util.UUID;

public record ResolvedPermission(String name, UUID groupScopeId) {}
