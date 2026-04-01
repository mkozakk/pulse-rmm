package dev.pulsermm.gateway.api;

import dev.pulsermm.gateway.infrastructure.identity.IdentityClient;
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
}
