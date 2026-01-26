package dev.pulsermm.gateway.api;

import dev.pulsermm.gateway.infrastructure.identity.IdentityClient;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionGuardTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

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

        Authentication auth = authWith(userId);
        assertThat(guard.canOpenShell(auth, endpointId.toString())).isTrue();
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

        Authentication auth = authWith(userId);
        assertThat(guard.canOpenShell(auth, endpointId.toString())).isFalse();
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

        Authentication auth = authWith(userId);
        assertThat(guard.canOpenShell(auth, endpointId.toString())).isTrue();
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

        Authentication auth = authWith(userId);
        assertThat(guard.canOpenShell(auth, endpointId.toString())).isFalse();
    }

    @Test
    void nullAuthCannotOpenShell() {
        UUID endpointId = UUID.randomUUID();
        PermissionGuard guard = new PermissionGuard(identityClient);

        assertThat(guard.canOpenShell(null, endpointId.toString())).isFalse();
    }

    private Authentication authWith(UUID userId) {
        String token = Jwts.builder()
            .subject(userId.toString())
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(KEY)
            .compact();

        Claims claims = Jwts.parser()
            .verifyWith(KEY)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        return new UsernamePasswordAuthenticationToken(claims, null, AuthorityUtils.NO_AUTHORITIES);
    }
}
