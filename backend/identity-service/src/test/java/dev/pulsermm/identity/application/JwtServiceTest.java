package dev.pulsermm.identity.application;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "this-is-a-32-byte-test-secret-!!";

    private JwtProperties props;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        props = new JwtProperties(SECRET, "pulse-rmm", Duration.ofMinutes(15), Duration.ofDays(7), false, 4);
        jwtService = new JwtService(props);
        jwtService.init();
    }

    @Test
    void shortSecretFailsOnInit() {
        var badProps = new JwtProperties("tooshort", "pulse-rmm", Duration.ofMinutes(15), Duration.ofDays(7), false, 4);
        var svc = new JwtService(badProps);
        assertThatThrownBy(svc::init).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tamperedSignatureRejected() {
        String token = jwtService.issue(UUID.randomUUID(), List.of("ADMIN"));
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");
        assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedPayloadRejected() {
        String token = jwtService.issue(UUID.randomUUID(), List.of("ADMIN"));
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        String modified = payload.replace("ADMIN", "SUPERADMIN");
        String newPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(modified.getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + newPayload + "." + parts[2];
        assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void clockSkewBoundary() {
        UUID userId = UUID.randomUUID();

        // expired 31s ago — beyond the 30s skew window
        String tooOld = jwtService.issue(userId, List.of("ADMIN"), Instant.now().minusSeconds(900 + 31));
        assertThatThrownBy(() -> jwtService.parse(tooOld)).isInstanceOf(ExpiredJwtException.class);

        // expired 29s ago — still within the 30s skew window
        String withinSkew = jwtService.issue(userId, List.of("ADMIN"), Instant.now().minusSeconds(900 + 29));
        assertThatNoException().isThrownBy(() -> jwtService.parse(withinSkew));
    }

    @Test
    void wrongIssuerRejected() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
            .issuer("evil")
            .subject(UUID.randomUUID().toString())
            .expiration(Date.from(Instant.now().plusSeconds(900)))
            .signWith(key)
            .compact();
        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void differentSecretRejected() {
        SecretKey otherKey = Keys.hmacShaKeyFor("another-secret-that-is-32-bytes!!".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
            .issuer("pulse-rmm")
            .subject(UUID.randomUUID().toString())
            .expiration(Date.from(Instant.now().plusSeconds(900)))
            .signWith(otherKey)
            .compact();
        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void roundTripPreservesSubAndRoles() {
        UUID userId = UUID.randomUUID();
        List<String> roles = List.of("ADMIN");

        Claims claims = jwtService.parse(jwtService.issue(userId, roles));

        assertThat(UUID.fromString(claims.getSubject())).isEqualTo(userId);
        assertThat(jwtService.getRoles(claims)).isEqualTo(roles);
    }

    @Test
    void expiryMatchesAccessTtl() {
        Claims claims = jwtService.parse(jwtService.issue(UUID.randomUUID(), List.of("ADMIN")));
        long diffMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(diffMs).isBetween(
            props.accessTtl().toMillis() - 1000,
            props.accessTtl().toMillis() + 1000
        );
    }

    @Test
    void missingRolesClaimParsesToEmptyList() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
            .issuer("pulse-rmm")
            .subject(UUID.randomUUID().toString())
            .expiration(Date.from(Instant.now().plusSeconds(900)))
            .signWith(key)
            .compact();
        assertThat(jwtService.getRoles(jwtService.parse(token))).isEmpty();
    }

    @Test
    void concurrentIssueProducesDistinctTokens() throws InterruptedException {
        int count = 100;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        Set<String> tokens = ConcurrentHashMap.newKeySet();
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < count; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                    String token = jwtService.issue(UUID.randomUUID(), List.of("ADMIN"));
                    jwtService.parse(token);
                    tokens.add(token);
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await();

        assertThat(failures).hasValue(0);
        assertThat(tokens).hasSize(count);
    }
}
