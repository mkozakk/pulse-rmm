package dev.pulsermm.gateway.api;

import java.util.List;
import java.util.UUID;

public class PermissionChecker {

    static boolean hasPermission(List<ResolvedPermission> perms, String required, UUID targetGroupId) {
        return perms.stream().anyMatch(p ->
            p.name().equals(required) && (p.groupScopeId() == null || p.groupScopeId().equals(targetGroupId))
        );
    }
}
