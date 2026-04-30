package dev.pulsermm.gateway.api;

import dev.pulsermm.gateway.infrastructure.identity.IdentityClient;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PermissionGuard {

    private final IdentityClient identityClient;

    public PermissionGuard(IdentityClient identityClient) {
        this.identityClient = identityClient;
    }

    public boolean canOpenShell(Authentication auth, String endpointId) {
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Claims claims)) {
            return false;
        }

        String userId = claims.getSubject();
        var perms = identityClient.getPermissions(userId);
        UUID groupId = identityClient.getEndpointGroup(endpointId).orElse(null);

        return PermissionChecker.hasPermission(perms, "remote:shell", groupId);
    }
}
