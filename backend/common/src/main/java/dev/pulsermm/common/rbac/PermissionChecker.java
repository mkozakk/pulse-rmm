package dev.pulsermm.common.rbac;

import java.util.List;
import java.util.UUID;

public class PermissionChecker {

    public static boolean hasPermission(List<ResolvedPermission> perms, String required, UUID targetGroupId) {
        return perms.stream().anyMatch(p ->
            p.name().equals(required) && (p.groupScopeId() == null || p.groupScopeId().equals(targetGroupId))
        );
    }
}
