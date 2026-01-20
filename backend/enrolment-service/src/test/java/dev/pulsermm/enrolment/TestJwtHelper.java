package dev.pulsermm.enrolment;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public class TestJwtHelper {
    private final String jwtSecret;

    public TestJwtHelper(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String createToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
            .subject("test-user")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(3600)))
            .signWith(key)
            .compact();
    }
}
