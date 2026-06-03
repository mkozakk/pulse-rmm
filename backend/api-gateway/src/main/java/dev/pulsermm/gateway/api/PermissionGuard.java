package dev.pulsermm.gateway.api;

import dev.pulsermm.common.rbac.IdentityClient;
import dev.pulsermm.common.rbac.PermissionChecker;
import dev.pulsermm.common.rbac.ResolvedPermission;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PermissionGuard {

    private final IdentityClient identityClient;

    public PermissionGuard(IdentityClient identityClient) {
        this.identityClient = identityClient;
    }

    private String userId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || !(auth instanceof JwtAuthenticationToken)) {
            return null;
        }
        return auth.getName();
    }

    public boolean canOpenShell(Authentication auth, String endpointId) {
        String userId = userId(auth);
        if (userId == null) {
            return false;
        }
        var perms = identityClient.getPermissions(userId);
        UUID groupId = identityClient.getEndpointGroup(endpointId).orElse(null);
        return PermissionChecker.hasPermission(perms, "remote:shell", groupId);
    }

    public boolean canManageStructure(Authentication auth) {
        String userId = userId(auth);
        if (userId == null) {
            return false;
        }
        var perms = identityClient.getPermissions(userId);
        return PermissionChecker.hasPermission(perms, "endpoint:structure:manage", null);
    }

    public boolean canManageAlerts(Authentication auth) {
        String userId = userId(auth);
        if (userId == null) {
            return false;
        }
        var perms = identityClient.getPermissions(userId);
        return PermissionChecker.hasPermission(perms, "alert:manage", null);
    }

    // Endpoint-scoped checks — fetches the endpoint's group then checks scope.
    public boolean canViewEndpoint(Authentication auth, String endpointId) {
        return hasEndpointPermission(auth, endpointId, "endpoint:view");
    }

    public boolean canActOnEndpoint(Authentication auth, String endpointId) {
        return hasEndpointPermission(auth, endpointId, "endpoint:act");
    }

    public boolean canViewSoftware(Authentication auth, String endpointId) {
        return hasEndpointPermission(auth, endpointId, "software:view");
    }

    public boolean canManageSoftware(Authentication auth, String endpointId) {
        return hasEndpointPermission(auth, endpointId, "software:manage");
    }

    public boolean canBrowseFiles(Authentication auth, String endpointId) {
        return hasEndpointPermission(auth, endpointId, "remote:file");
    }

    // Global endpoint:view — used when there is no specific endpoint to scope against
    // (e.g. listing all endpoints, reading groups, reading tag-rules).
    public boolean canViewEndpoints(Authentication auth) {
        return hasGlobalPermission(auth, "endpoint:view");
    }

    // Global checks (no group scope).
    public boolean canViewScripts(Authentication auth) {
        return hasGlobalPermission(auth, "script:run");
    }

    public boolean canCreateScripts(Authentication auth) {
        return hasGlobalPermission(auth, "script:adhoc");
    }

    public boolean canApproveScripts(Authentication auth) {
        return hasGlobalPermission(auth, "script:approve");
    }

    public boolean canViewAudit(Authentication auth) {
        return hasGlobalPermission(auth, "audit:view");
    }

    public boolean canExportAudit(Authentication auth) {
        return hasGlobalPermission(auth, "audit:export");
    }

    public boolean canManageAgentVersions(Authentication auth) {
        return hasGlobalPermission(auth, "agent:manage");
    }

    public boolean canManageIntegrations(Authentication auth) {
        return hasGlobalPermission(auth, "integration:manage");
    }

    public boolean canManageEnrolment(Authentication auth) {
        return hasGlobalPermission(auth, "enrolment:manage");
    }

    private boolean hasEndpointPermission(Authentication auth, String endpointId, String permission) {
        String userId = userId(auth);
        if (userId == null) return false;
        var perms = identityClient.getPermissions(userId);
        UUID groupId = identityClient.getEndpointGroup(endpointId).orElse(null);
        return PermissionChecker.hasPermission(perms, permission, groupId);
    }

    private boolean hasGlobalPermission(Authentication auth, String permission) {
        String userId = userId(auth);
        if (userId == null) return false;
        var perms = identityClient.getPermissions(userId);
        return PermissionChecker.hasPermission(perms, permission, null);
    }
}
