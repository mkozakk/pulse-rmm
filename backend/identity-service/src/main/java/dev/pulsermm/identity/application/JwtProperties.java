package dev.pulsermm.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties("pulse.jwt")
public record JwtProperties(
    String secret,
    String issuer,
    Duration accessTtl,
    Duration refreshTtl,
    boolean cookieSecure,
    int bcryptStrength
) {}
