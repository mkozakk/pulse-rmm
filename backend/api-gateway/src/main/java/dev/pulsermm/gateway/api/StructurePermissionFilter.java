package dev.pulsermm.gateway.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class StructurePermissionFilter extends OncePerRequestFilter {

    private final PermissionGuard permissionGuard;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public StructurePermissionFilter(PermissionGuard permissionGuard) {
        this.permissionGuard = permissionGuard;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (requiresStructurePermission(request)) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (!permissionGuard.canManageStructure(auth)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Forbidden\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean requiresStructurePermission(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();

        if ("POST".equals(method) && "/api/groups".equals(path)) return true;
        if ("PUT".equals(method) && matcher.match("/api/endpoints/*/group", path)) return true;
        if ("PUT".equals(method) && matcher.match("/api/endpoints/*/tags", path)) return true;
        if (("POST".equals(method) || "DELETE".equals(method)) &&
                (path.equals("/api/tag-rules") || path.startsWith("/api/tag-rules/"))) return true;
        return false;
    }
}
