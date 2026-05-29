package dev.pulsermm.gateway.api;

import dev.pulsermm.gateway.infrastructure.identity.IdentityClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionGuardTest {

    @Mock
    IdentityClient identityClient;

    @Test
    void userWithRemoteShellPermissionCanOpenShell() {
        UUID userId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        PermissionGuard guard = new PermissionGuard(identityClient);

        when(identityClient.getPermissions(userId.toString()))
            .thenReturn(List.of(new ResolvedPermission("remote:shell", null)));
        when(identityClient.getEndpointGroup(endpointId.toString()))
            .thenReturn(Optional.empty());

        assertThat(guard.canOpenShell(authWith(userId), endpointId.toString())).isTrue();
    }

    @Test
    void userWithoutRemoteShellPermissionCannotOpenShell() {
        UUID userId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        PermissionGuard guard = new PermissionGuard(identityClient);

        when(identityClient.getPermissions(userId.toString()))
            .thenReturn(List.of(new ResolvedPermission("endpoint:view", null)));
        when(identityClient.getEndpointGroup(endpointId.toString()))
            .thenReturn(Optional.empty());

        assertThat(guard.canOpenShell(authWith(userId), endpointId.toString())).isFalse();
    }

    @Test
    void userWithScopedShellPermissionCanOpenInCorrectGroup() {
        UUID userId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        PermissionGuard guard = new PermissionGuard(identityClient);

        when(identityClient.getPermissions(userId.toString()))
            .thenReturn(List.of(new ResolvedPermission("remote:shell", groupId)));
        when(identityClient.getEndpointGroup(endpointId.toString()))
            .thenReturn(Optional.of(groupId));

        assertThat(guard.canOpenShell(authWith(userId), endpointId.toString())).isTrue();
    }

    @Test
    void userWithScopedShellPermissionCannotOpenInDifferentGroup() {
        UUID userId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        UUID groupA = UUID.randomUUID();
        UUID groupB = UUID.randomUUID();
        PermissionGuard guard = new PermissionGuard(identityClient);

        when(identityClient.getPermissions(userId.toString()))
            .thenReturn(List.of(new ResolvedPermission("remote:shell", groupA)));
        when(identityClient.getEndpointGroup(endpointId.toString()))
            .thenReturn(Optional.of(groupB));

        assertThat(guard.canOpenShell(authWith(userId), endpointId.toString())).isFalse();
    }

    @Test
    void nullAuthCannotOpenShell() {
        PermissionGuard guard = new PermissionGuard(identityClient);
        assertThat(guard.canOpenShell(null, UUID.randomUUID().toString())).isFalse();
    }

    @Test
    void userWithStructureManagePermissionCanManageStructure() {
        UUID userId = UUID.randomUUID();
        PermissionGuard guard = new PermissionGuard(identityClient);

        when(identityClient.getPermissions(userId.toString()))
            .thenReturn(List.of(new ResolvedPermission("endpoint:structure:manage", null)));

        assertThat(guard.canManageStructure(authWith(userId))).isTrue();
    }

    @Test
    void userWithoutStructureManagePermissionCannotManageStructure() {
        UUID userId = UUID.randomUUID();
        PermissionGuard guard = new PermissionGuard(identityClient);

        when(identityClient.getPermissions(userId.toString()))
            .thenReturn(List.of(new ResolvedPermission("endpoint:view", null)));

        assertThat(guard.canManageStructure(authWith(userId))).isFalse();
    }

    private Authentication authWith(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(userId.toString())
            .build();
        // two-arg constructor marks the token authenticated, like the real resource-server filter
        return new JwtAuthenticationToken(jwt, java.util.Collections.emptyList());
    }
}
