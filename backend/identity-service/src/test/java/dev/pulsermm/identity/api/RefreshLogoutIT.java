package dev.pulsermm.identity.api;

import dev.pulsermm.identity.application.RefreshTokenService;
import dev.pulsermm.identity.domain.RefreshToken;
import dev.pulsermm.identity.domain.User;
import dev.pulsermm.identity.infrastructure.RefreshTokenRepository;
import dev.pulsermm.identity.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RefreshLogoutIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository userRepository;
    @SpyBean RefreshTokenRepository refreshTokenRepository;
    @Autowired RefreshTokenService refreshTokenService;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDb() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void resetSpy() {
        Mockito.reset(refreshTokenRepository);
    }

    private HttpEntity<Object> json(Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }

    private String loginAndGetRefreshCookie() {
        createUser("admin", "validpassword12");
        return login("admin", "validpassword12");
    }

    private void createUser(String username, String password) {
        userRepository.save(new User(username, passwordEncoder.encode(password)));
    }

    private String login(String username, String password) {
        ResponseEntity<Map> response = rest.postForEntity("/api/auth/login",
            json(Map.of("username", username, "password", password)), Map.class);
        return response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    }

    private HttpHeaders cookieHeaders(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie.split(";", 2)[0]);
        return headers;
    }

    private static String rawToken(String cookie) {
        return cookie.split(";", 2)[0].substring("pulse_refresh=".length());
    }

    private RefreshToken tokenFromCookie(String cookie) {
        return refreshTokenRepository.findByTokenHash(sha256hex(rawToken(cookie))).orElseThrow();
    }

    @Test
    void refreshWithoutCookieReturns401() {
        var response = rest.postForEntity("/api/auth/refresh", new HttpEntity<>(new HttpHeaders()), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().get("error")).isEqualTo("invalid_refresh_token");
    }

    @Test
    void refreshWithUnknownCookieReturns401() {
        HttpHeaders headers = cookieHeaders("pulse_refresh=unknown");
        var response = rest.postForEntity("/api/auth/refresh", new HttpEntity<>(headers), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().get("error")).isEqualTo("invalid_refresh_token");
    }

    @Test
    void refreshRotatesToken() {
        String cookie = loginAndGetRefreshCookie();

        var response = rest.postForEntity("/api/auth/refresh", new HttpEntity<>(cookieHeaders(cookie)), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat((String) response.getBody().get("accessToken")).isNotBlank();
        String newCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(newCookie).isNotEqualTo(cookie);
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
        assertThat(refreshTokenRepository.findAll().stream().filter(t -> t.getRevokedAt() != null)).hasSize(1);
        assertThat(refreshTokenRepository.findAll().stream().filter(t -> t.getRevokedAt() == null)).hasSize(1);
    }

    @Test
    void refreshWithRevokedTokenRevokesAllTokens() {
        createUser("admin", "validpassword12");
        String cookie = login("admin", "validpassword12");
        String otherCookie = login("admin", "validpassword12");

        RefreshToken revoked = tokenFromCookie(cookie);
        revoked.setRevokedAt(OffsetDateTime.now());
        refreshTokenRepository.save(revoked);
        assertThat(tokenFromCookie(otherCookie).getRevokedAt()).isNull();

        var response = rest.postForEntity("/api/auth/refresh", new HttpEntity<>(cookieHeaders(cookie)), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(refreshTokenRepository.findAll().stream().allMatch(t -> t.getRevokedAt() != null)).isTrue();
    }

    @Test
    void replayingOriginalRefreshTokenRevokesRotatedToken() {
        String cookie = loginAndGetRefreshCookie();

        var rotated = rest.postForEntity("/api/auth/refresh", new HttpEntity<>(cookieHeaders(cookie)), Map.class);
        String rotatedCookie = rotated.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        var replay = rest.postForEntity("/api/auth/refresh", new HttpEntity<>(cookieHeaders(cookie)), Map.class);
        assertThat(replay.getStatusCode().value()).isEqualTo(401);

        var rotatedResponse = rest.postForEntity("/api/auth/refresh", new HttpEntity<>(cookieHeaders(rotatedCookie)), Map.class);
        assertThat(rotatedResponse.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void refreshWithExpiredTokenReturns401WithoutCascade() {
        createUser("admin", "validpassword12");
        String cookie = login("admin", "validpassword12");
        String otherCookie = login("admin", "validpassword12");

        RefreshToken expired = tokenFromCookie(cookie);
        expired.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        refreshTokenRepository.save(expired);

        var response = rest.postForEntity("/api/auth/refresh", new HttpEntity<>(cookieHeaders(cookie)), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(tokenFromCookie(otherCookie).getRevokedAt()).isNull();
    }

    @Test
    void refreshNearExpiryStillSucceeds() {
        String cookie = loginAndGetRefreshCookie();
        RefreshToken token = tokenFromCookie(cookie);
        token.setExpiresAt(OffsetDateTime.now().plusSeconds(10));
        refreshTokenRepository.save(token);

        var response = rest.postForEntity("/api/auth/refresh", new HttpEntity<>(cookieHeaders(cookie)), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void refreshForDeletedUserReturns401() {
        String cookie = loginAndGetRefreshCookie();
        User user = userRepository.findByUsername("admin").orElseThrow();
        userRepository.delete(user);

        var response = rest.postForEntity("/api/auth/refresh", new HttpEntity<>(cookieHeaders(cookie)), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void logoutWithoutCookieIsNoop() {
        var response = rest.postForEntity("/api/auth/logout", new HttpEntity<>(new HttpHeaders()), Void.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("pulse_refresh=");
    }

    @Test
    void logoutWithCookieRevokesToken() {
        String cookie = loginAndGetRefreshCookie();

        var response = rest.postForEntity("/api/auth/logout", new HttpEntity<>(cookieHeaders(cookie)), Void.class);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(refreshTokenRepository.findAll().stream().allMatch(t -> t.getRevokedAt() != null)).isTrue();
    }

    @Test
    void logoutTwiceDoesNotRevokeOtherTokens() {
        createUser("admin", "validpassword12");
        String cookie = login("admin", "validpassword12");
        String otherCookie = login("admin", "validpassword12");

        var first = rest.postForEntity("/api/auth/logout", new HttpEntity<>(cookieHeaders(cookie)), Void.class);
        var second = rest.postForEntity("/api/auth/logout", new HttpEntity<>(cookieHeaders(cookie)), Void.class);

        assertThat(first.getStatusCode().value()).isEqualTo(204);
        assertThat(second.getStatusCode().value()).isEqualTo(204);
        assertThat(tokenFromCookie(otherCookie).getRevokedAt()).isNull();
    }

    @Test
    void refreshAfterLogoutTriggersCascade() {
        createUser("admin", "validpassword12");
        String cookie = login("admin", "validpassword12");
        String otherCookie = login("admin", "validpassword12");

        rest.postForEntity("/api/auth/logout", new HttpEntity<>(cookieHeaders(cookie)), Void.class);
        var response = rest.postForEntity("/api/auth/refresh", new HttpEntity<>(cookieHeaders(cookie)), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(tokenFromCookie(otherCookie).getRevokedAt()).isNotNull();
    }

    @Test
    void rotationRollbackOnInsertFailure() {
        createUser("admin", "validpassword12");
        User user = userRepository.findByUsername("admin").orElseThrow();
        String raw = "raw-token";
        refreshTokenService.issue(user, raw);

        Mockito.doAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            if (token.getRevokedAt() == null) {
                throw new RuntimeException("boom");
            }
            return invocation.callRealMethod();
        }).when(refreshTokenRepository).save(Mockito.any(RefreshToken.class));

        assertThatThrownBy(() -> refreshTokenService.rotate(raw)).isInstanceOf(RuntimeException.class);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256hex(raw)).orElseThrow();
        assertThat(stored.getRevokedAt()).isNull();
    }

    @Test
    void reuseCascadeDoesNotAffectOtherUsers() {
        createUser("admin", "validpassword12");
        createUser("tech", "validpassword12");
        String adminCookie = login("admin", "validpassword12");
        String techCookie = login("tech", "validpassword12");

        var rotated = rest.postForEntity("/api/auth/refresh", new HttpEntity<>(cookieHeaders(adminCookie)), Map.class);
        String rotatedCookie = rotated.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        var replay = rest.postForEntity("/api/auth/refresh", new HttpEntity<>(cookieHeaders(adminCookie)), Map.class);
        assertThat(replay.getStatusCode().value()).isEqualTo(401);
        assertThat(rotatedCookie).isNotNull();

        assertThat(tokenFromCookie(techCookie).getRevokedAt()).isNull();
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
