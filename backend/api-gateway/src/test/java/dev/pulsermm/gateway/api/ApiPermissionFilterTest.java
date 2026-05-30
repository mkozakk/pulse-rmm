package dev.pulsermm.gateway.api;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiPermissionFilterTest {

    @Mock
    PermissionGuard guard;
    @Mock
    FilterChain chain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        SecurityContextHolder.clearContext();
        var request = request("GET", "/api/scripts");
        var response = new MockHttpServletResponse();

        new ApiPermissionFilter(guard).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void scriptCreateRequiresAdhocPermission() throws Exception {
        authenticate();
        when(guard.canCreateScripts(any())).thenReturn(false);

        var response = run("POST", "/api/scripts");

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test
    void scriptCreatePassesWithAdhocPermission() throws Exception {
        authenticate();
        when(guard.canCreateScripts(any())).thenReturn(true);

        var response = run("POST", "/api/scripts");

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void auditViewRequiresAuditViewPermission() throws Exception {
        authenticate();
        when(guard.canViewAudit(any())).thenReturn(false);

        var response = run("GET", "/api/audit");

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void auditExportRequiresAuditExportPermission() throws Exception {
        authenticate();
        when(guard.canExportAudit(any())).thenReturn(true);

        var response = run("GET", "/api/audit/export");

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void softwareManageRequiresManagePermission() throws Exception {
        authenticate();
        UUID endpointId = UUID.randomUUID();
        when(guard.canManageSoftware(any(), eq(endpointId.toString()))).thenReturn(false);

        var response = run("POST", "/api/endpoints/" + endpointId + "/software/install");

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void processKillRequiresActPermission() throws Exception {
        authenticate();
        UUID endpointId = UUID.randomUUID();
        when(guard.canActOnEndpoint(any(), eq(endpointId.toString()))).thenReturn(true);

        var response = run("POST", "/api/endpoints/" + endpointId + "/processes/1234/kill");

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void webhookAccessRequiresIntegrationManagePermission() throws Exception {
        authenticate();
        when(guard.canManageIntegrations(any())).thenReturn(false);

        var response = run("GET", "/api/webhooks");

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void unmonitoredPathPassesThrough() throws Exception {
        authenticate();
        var response = run("GET", "/api/sessions/some-id");

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    private MockHttpServletResponse run(String method, String path) throws Exception {
        var request = request(method, path);
        var response = new MockHttpServletResponse();
        new ApiPermissionFilter(guard).doFilter(request, response, chain);
        return response;
    }

    private static MockHttpServletRequest request(String method, String path) {
        var req = new MockHttpServletRequest(method, path);
        req.setServletPath(path);
        return req;
    }

    private static void authenticate() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .build();
        var auth = new JwtAuthenticationToken(jwt, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
