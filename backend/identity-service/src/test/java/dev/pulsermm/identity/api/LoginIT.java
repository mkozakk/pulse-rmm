package dev.pulsermm.identity.api;

import dev.pulsermm.identity.application.JwtService;
import dev.pulsermm.identity.infrastructure.RefreshTokenRepository;
import dev.pulsermm.identity.infrastructure.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LoginIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JwtService jwtService;

    @BeforeEach
    void cleanDb() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private HttpEntity<Object> json(Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }

    private void register(String username, String password) {
        rest.postForEntity("/api/auth/register", json(Map.of("username", username, "password", password)), Void.class);
    }

    @Test
    void wrongPasswordReturns401NoTokenMinted() {
        register("admin", "validpassword12");
        var response = rest.postForEntity("/api/auth/login",
            json(Map.of("username", "admin", "password", "wrongpassword1")), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().get("error")).isEqualTo("invalid_credentials");
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void unknownUsernameReturns401() {
        var response = rest.postForEntity("/api/auth/login",
            json(Map.of("username", "nobody", "password", "validpassword12")), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().get("error")).isEqualTo("invalid_credentials");
    }

    @Test
    void timingParitySmoke() {
        register("admin", "validpassword12");
        for (int i = 0; i < 3; i++) {
            rest.postForEntity("/api/auth/login", json(Map.of("username", "admin", "password", "wrongpassword1")), Void.class);
            rest.postForEntity("/api/auth/login", json(Map.of("username", "notexist", "password", "wrongpassword1")), Void.class);
        }

        long[] wrongPwd = new long[20];
        long[] unknownUser = new long[20];
        for (int i = 0; i < 20; i++) {
            long s = System.nanoTime();
            rest.postForEntity("/api/auth/login", json(Map.of("username", "admin", "password", "wrongpassword1")), Void.class);
            wrongPwd[i] = System.nanoTime() - s;

            s = System.nanoTime();
            rest.postForEntity("/api/auth/login", json(Map.of("username", "notexist", "password", "wrongpassword1")), Void.class);
            unknownUser[i] = System.nanoTime() - s;
        }

        Arrays.sort(wrongPwd);
        Arrays.sort(unknownUser);
        double ratio = (double) Math.max(wrongPwd[10], unknownUser[10]) / Math.min(wrongPwd[10], unknownUser[10]);
        assertThat(ratio).isLessThan(3.0);
    }

    @Test
    void correctCredsReturnsTokenWithClaims() {
        register("admin", "validpassword12");
        var user = userRepository.findByUsername("admin").orElseThrow();

        var response = rest.postForEntity("/api/auth/login",
            json(Map.of("username", "admin", "password", "validpassword12")), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().get("expiresIn")).isEqualTo(900);

        String accessToken = (String) response.getBody().get("accessToken");
        assertThat(accessToken).isNotBlank();

        Claims claims = jwtService.parse(accessToken);
        assertThat(UUID.fromString(claims.getSubject())).isEqualTo(user.getId());
        assertThat(jwtService.getRoles(claims)).isEqualTo(List.of("ADMIN"));
        assertThat(claims.getIssuer()).isEqualTo("pulse-rmm");
        long ttl = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(ttl).isEqualTo(900);
    }

    @Test
    void correctCredsSetsCookieCorrectly() {
        register("admin", "validpassword12");
        var response = rest.postForEntity("/api/auth/login",
            json(Map.of("username", "admin", "password", "validpassword12")), Map.class);

        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(1);
        String cookie = setCookies.get(0);
        assertThat(cookie).contains("pulse_refresh=");
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("Path=/api/auth");
        assertThat(cookie).contains("SameSite=Lax");
        assertThat(cookie).contains("Max-Age=604800");
        assertThat(cookie).doesNotContain("Secure");

        String rawToken = cookie.split(";")[0].substring("pulse_refresh=".length());
        assertThat(rawToken).doesNotContain("=", "+", "/");
    }

    @Test
    void tokenIsHashedInDb() {
        register("admin", "validpassword12");
        var response = rest.postForEntity("/api/auth/login",
            json(Map.of("username", "admin", "password", "validpassword12")), Map.class);

        String cookie = response.getHeaders().get(HttpHeaders.SET_COOKIE).get(0);
        String rawToken = cookie.split(";")[0].substring("pulse_refresh=".length());

        assertThat(refreshTokenRepository.findByTokenHash(rawToken)).isEmpty();
        var rt = refreshTokenRepository.findByTokenHash(sha256hex(rawToken)).orElseThrow();
        assertThat(rt.getRevokedAt()).isNull();
        assertThat(rt.getExpiresAt()).isBetween(
            OffsetDateTime.now().plusDays(6).plusHours(23),
            OffsetDateTime.now().plusDays(7).plusHours(1)
        );
    }

    @Test
    void loginCaseSensitiveUsername() {
        register("admin", "validpassword12");
        var response = rest.postForEntity("/api/auth/login",
            json(Map.of("username", "Admin", "password", "validpassword12")), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void twoLoginsProduceTwoNonRevokedTokens() {
        register("admin", "validpassword12");
        rest.postForEntity("/api/auth/login", json(Map.of("username", "admin", "password", "validpassword12")), Void.class);
        rest.postForEntity("/api/auth/login", json(Map.of("username", "admin", "password", "validpassword12")), Void.class);
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
        assertThat(refreshTokenRepository.findAll().stream().allMatch(t -> t.getRevokedAt() == null)).isTrue();
    }

    private static String sha256hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
