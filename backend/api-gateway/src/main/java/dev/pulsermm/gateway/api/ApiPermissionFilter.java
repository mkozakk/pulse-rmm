package dev.pulsermm.gateway.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ApiPermissionFilter extends OncePerRequestFilter {

    private final PermissionGuard guard;

    public ApiPermissionFilter(PermissionGuard guard) {
        this.guard = guard;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getServletPath();
        String method = request.getMethod();

        if (!isMonitored(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Permit-all paths within monitored prefixes (internal agent callbacks, no JWT)
        if (isPermitAll(path, method)) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // auth is null here (AnonymousAuthenticationFilter runs later in the chain);
        // treat missing/unauthenticated as 403 so callers can't distinguish missing
        // auth from missing permission.
        if (auth == null || !auth.isAuthenticated()) {
            send(response, 403, "Forbidden");
            return;
        }

        if (!isAllowed(auth, path, method)) {
            send(response, 403, "Forbidden");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPermitAll(String path, String method) {
        // Agent ack callback: gateway calls this without a user JWT when relaying ack events
        if ("POST".equals(method) && path.matches("/api/scripts/runs/[^/]+/endpoints/[^/]+/ack")) {
            return true;
        }
        // Checksum endpoint is called by the install script before the agent is enrolled (no JWT)
        if ("GET".equals(method) && path.equals("/api/agent-versions/checksum")) {
            return true;
        }
        return false;
    }

    private boolean isMonitored(String path) {
        return path.startsWith("/api/endpoints")
            || path.startsWith("/api/files/")
            || path.startsWith("/api/scripts")
            || path.startsWith("/api/audit")
            || path.startsWith("/api/agent-versions")
            || path.startsWith("/api/webhooks")
            || path.startsWith("/api/deliveries")
            || path.startsWith("/api/enrolment")
            || path.startsWith("/api/groups")
            || path.startsWith("/api/tag-rules");
    }

    private boolean isAllowed(Authentication auth, String path, String method) {
        if (path.startsWith("/api/files/")) {
            return checkFilesRoute(auth, path);
        }
        if (path.startsWith("/api/endpoints/") || path.equals("/api/endpoints")) {
            return checkEndpointsRoute(auth, path, method);
        }
        if (path.startsWith("/api/scripts")) {
            return checkScriptsRoute(auth, path, method);
        }
        if (path.startsWith("/api/audit")) {
            return checkAuditRoute(auth, path);
        }
        if (path.startsWith("/api/agent-versions")) {
            return guard.canManageAgentVersions(auth);
        }
        if (path.startsWith("/api/webhooks") || path.startsWith("/api/deliveries")) {
            return guard.canManageIntegrations(auth);
        }
        if (path.startsWith("/api/enrolment")) {
            return guard.canManageEnrolment(auth);
        }
        if (path.startsWith("/api/groups") || path.startsWith("/api/tag-rules")) {
            return guard.canViewEndpoints(auth);
        }
        return true;
    }

    private boolean checkFilesRoute(Authentication auth, String path) {
        String endpointId = segment(path, 3);
        if (endpointId == null) return false;
        return guard.canBrowseFiles(auth, endpointId);
    }

    private boolean checkEndpointsRoute(Authentication auth, String path, String method) {
        if (path.equals("/api/endpoints")) {
            return guard.canViewEndpoints(auth);
        }

        String endpointId = segment(path, 3);
        if (endpointId == null) return false;

        // Tag and group writes are handled upstream by StructurePermissionFilter — skip.
        if (("PUT".equals(method) || "POST".equals(method))
                && (path.endsWith("/group") || path.endsWith("/tags"))) {
            return true;
        }

        if (path.contains("/software")) {
            return "GET".equals(method)
                ? guard.canViewSoftware(auth, endpointId)
                : guard.canManageSoftware(auth, endpointId);
        }

        if (path.contains("/processes")) {
            return "GET".equals(method)
                ? guard.canViewEndpoint(auth, endpointId)
                : guard.canActOnEndpoint(auth, endpointId);
        }

        if (path.endsWith("/revoke")) {
            return guard.canManageEnrolment(auth);
        }

        // metrics, system-info, or the endpoint itself
        return guard.canViewEndpoint(auth, endpointId);
    }

    private boolean checkScriptsRoute(Authentication auth, String path, String method) {
        if (path.equals("/api/scripts")) {
            return "GET".equals(method) ? guard.canViewScripts(auth) : guard.canCreateScripts(auth);
        }
        if (path.endsWith("/approve") && "POST".equals(method)) {
            return guard.canApproveScripts(auth);
        }
        // runs/ack and runs/results both require script:run
        return guard.canViewScripts(auth);
    }

    private boolean checkAuditRoute(Authentication auth, String path) {
        if (path.startsWith("/api/audit/export")) {
            return guard.canExportAudit(auth);
        }
        return guard.canViewAudit(auth);
    }

    // Splits "/api/endpoints/{id}/..." on "/" and returns the segment at the given index.
    // "/api/endpoints/abc" → ["", "api", "endpoints", "abc"] → index 3 = "abc"
    private static String segment(String path, int index) {
        String[] parts = path.split("/");
        return parts.length > index ? parts[index] : null;
    }

    private static void send(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
