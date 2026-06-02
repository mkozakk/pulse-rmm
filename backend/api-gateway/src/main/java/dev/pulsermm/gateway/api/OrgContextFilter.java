package dev.pulsermm.gateway.api;

import dev.pulsermm.gateway.infrastructure.identity.EndpointOrgClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

// Derives the caller's org from the JWT and injects a trusted X-User-Org-Id header on forwarded requests.
// Global admin (no org_id claim) gets no header — services then see all orgs. For org-scoped users it also
// validates that any endpoint in the path belongs to the caller's org before forwarding (chain validation).
public class OrgContextFilter extends OncePerRequestFilter {

    private final EndpointOrgClient endpointOrgClient;

    public OrgContextFilter(EndpointOrgClient endpointOrgClient) {
        this.endpointOrgClient = endpointOrgClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String orgId = callerOrg();

        if (orgId != null) {
            String endpointId = endpointIdFromPath(request.getServletPath());
            if (endpointId != null && !belongsToOrg(endpointId, orgId)) {
                // 404 (not 403) so an org user cannot probe for the existence of another org's endpoints.
                response.setStatus(404);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Not Found\"}");
                return;
            }
        }

        chain.doFilter(new OrgHeaderRequestWrapper(request, orgId), response);
    }

    private boolean belongsToOrg(String endpointId, String orgId) {
        Optional<UUID> targetOrg = endpointOrgClient.getEndpointOrg(endpointId);
        return targetOrg.isPresent() && orgId.equals(targetOrg.get().toString());
    }

    private String callerOrg() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String orgId = jwtAuth.getToken().getClaimAsString("org_id");
            return (orgId == null || orgId.isBlank()) ? null : orgId;
        }
        return null;
    }

    // /api/endpoints/{id}/... and /api/files/{id}/... carry an endpoint id in the third path segment.
    private String endpointIdFromPath(String path) {
        if (path == null) return null;
        if (!path.startsWith("/api/endpoints/") && !path.startsWith("/api/files/")) {
            return null;
        }
        String[] parts = path.split("/");
        return parts.length > 3 && !parts[3].isBlank() ? parts[3] : null;
    }
}
