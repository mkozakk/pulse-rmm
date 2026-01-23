package dev.pulsermm.gateway.api;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PermissionGuard {

    // TODO sprint 6: replace with PermissionEvaluator + group-scoped check
    public boolean canOpenShell(Authentication auth) {
        List<String> roles = extractRoles(auth);
        return roles.contains("admin") || roles.contains("senior_technician");
    }

    private List<String> extractRoles(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return List.of();
        if (auth.getPrincipal() instanceof Claims claims) {
            Object raw = claims.get("roles");
            if (raw instanceof List<?> list) {
                return list.stream().map(Object::toString).toList();
            }
        }
        return List.of();
    }
}
