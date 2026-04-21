package dev.pulsermm.identity.application;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties props;
    private SecretKey signingKey;

    public JwtService(JwtProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        byte[] bytes = props.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        signingKey = Keys.hmacShaKeyFor(bytes);
    }

    public String issue(UUID userId, List<String> roles) {
        return issue(userId, roles, Instant.now());
    }

    String issue(UUID userId, List<String> roles, Instant now) {
        return Jwts.builder()
            .subject(userId.toString())
            .issuer(props.issuer())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(props.accessTtl())))
            .claim("roles", roles)
            .signWith(signingKey)
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .requireIssuer(props.issuer())
            .clockSkewSeconds(30)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        List<?> roles = claims.get("roles", List.class);
        if (roles == null) return List.of();
        return roles.stream().map(Object::toString).toList();
    }
}
