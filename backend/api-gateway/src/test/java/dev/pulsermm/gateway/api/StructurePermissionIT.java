package dev.pulsermm.gateway.api;

import dev.pulsermm.gateway.infrastructure.identity.IdentityClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "pulse.jwt.secret=test-secret-key-that-is-long-enough-for-hs256",
    "grpc.server.port=0"
})
class StructurePermissionIT {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @MockBean
    IdentityClient identityClient;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void postGroupsWithPermissionPassesFilter() {
        UUID userId = UUID.randomUUID();
        when(identityClient.getPermissions(userId.toString()))
            .thenReturn(List.of(new ResolvedPermission("endpoint:structure:manage", null)));

        ResponseEntity<Void> response = restTemplate.exchange(
            "/api/groups", HttpMethod.POST,
            new HttpEntity<>(Map.of("name", "HQ"), authHeaders(userId)),
            Void.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void postGroupsWithoutPermissionReturnsForbidden() {
        UUID userId = UUID.randomUUID();
        when(identityClient.getPermissions(userId.toString()))
            .thenReturn(List.of(new ResolvedPermission("endpoint:view", null)));

        ResponseEntity<Void> response = restTemplate.exchange(
            "/api/groups", HttpMethod.POST,
            new HttpEntity<>(Map.of("name", "HQ"), authHeaders(userId)),
            Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getGroupsDoesNotRequireStructurePermission() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<Void> response = restTemplate.exchange(
            "/api/groups", HttpMethod.GET,
            new HttpEntity<>(authHeaders(userId)),
            Void.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    private HttpHeaders authHeaders(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + buildJwt(userId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String buildJwt(UUID userId) {
        return Jwts.builder()
            .subject(userId.toString())
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(KEY)
            .compact();
    }
}
