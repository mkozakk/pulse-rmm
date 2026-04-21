package dev.pulsermm.identity.application;

import dev.pulsermm.identity.domain.RefreshToken;
import dev.pulsermm.identity.domain.User;
import dev.pulsermm.identity.infrastructure.RefreshTokenRepository;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties props;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties props) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.props = props;
    }

    public String generate() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public RefreshToken issue(User user, String rawToken) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(OffsetDateTime.now().plus(props.refreshTtl()));
        return refreshTokenRepository.save(token);
    }

    public ResponseCookie buildCookie(String rawToken) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("pulse_refresh", rawToken)
            .httpOnly(true)
            .path("/api/auth")
            .sameSite("Lax")
            .maxAge(props.refreshTtl().getSeconds());
        if (props.cookieSecure()) {
            builder = builder.secure(true);
        }
        return builder.build();
    }
}
