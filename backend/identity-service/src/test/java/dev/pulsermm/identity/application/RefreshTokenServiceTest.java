package dev.pulsermm.identity.application;

import dev.pulsermm.identity.infrastructure.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    RefreshTokenService service;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(
            "test-secret", "pulse-rmm", Duration.ofMinutes(15), Duration.ofDays(7), false, 4
        );
        service = new RefreshTokenService(refreshTokenRepository, props);
    }

    @Test
    void hashMatchesSha256() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] raw = md.digest("abc".getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : raw) sb.append(String.format("%02x", b));
        String expected = sb.toString();

        String result = service.hash("abc");
        assertThat(result).isEqualTo(expected);
        assertThat(result).hasSize(64);
        assertThat(result).matches("[0-9a-f]+");
    }

    @Test
    void generateIsBase64UrlNoPadding() {
        String token = service.generate();
        assertThat(token).doesNotContain("=", "+", "/");
        byte[] bytes = Base64.getUrlDecoder().decode(token + "=");
        assertThat(bytes).hasSize(32);
    }

    @Test
    void generateReturnsDifferentValues() {
        assertThat(service.generate()).isNotEqualTo(service.generate());
    }

    @Test
    void buildCookieAttributes() {
        ResponseCookie insecure = service.buildCookie("some-token");
        String header = insecure.toString();
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("Path=/api/auth");
        assertThat(header).contains("SameSite=Lax");
        assertThat(header).contains("Max-Age=604800");
        assertThat(header).doesNotContain("Secure");

        JwtProperties secureProps = new JwtProperties(
            "test-secret", "pulse-rmm", Duration.ofMinutes(15), Duration.ofDays(7), true, 4
        );
        RefreshTokenService secureService = new RefreshTokenService(refreshTokenRepository, secureProps);
        assertThat(secureService.buildCookie("some-token").toString()).contains("Secure");
    }
}
