package dev.pulsermm.gateway.api;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionGuardTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void adminCanOpenShell() {
        assertThat(guard.canOpenShell(authWith(List.of("admin")))).isTrue();
    }

    @Test
    void seniorTechnicianCanOpenShell() {
        assertThat(guard.canOpenShell(authWith(List.of("senior_technician")))).isTrue();
    }

    @Test
    void juniorTechnicianCannotOpenShell() {
        assertThat(guard.canOpenShell(authWith(List.of("junior_technician")))).isFalse();
    }

    @Test
    void noRolesCannotOpenShell() {
        assertThat(guard.canOpenShell(authWith(List.of()))).isFalse();
    }

    @Test
    void nullAuthCannotOpenShell() {
        assertThat(guard.canOpenShell(null)).isFalse();
    }

    private Authentication authWith(List<String> roles) {
        String token = Jwts.builder()
            .subject("test-user")
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .claim("roles", roles)
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
