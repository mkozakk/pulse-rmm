package dev.pulsermm.gateway.infrastructure.mtls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "pulse.mtls.enabled", havingValue = "true")
public class RevocationChecker {

    private static final Logger logger = LoggerFactory.getLogger(RevocationChecker.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);

    private final RestClient client;
    private final ConcurrentHashMap<UUID, CachedEntry> cache = new ConcurrentHashMap<>();

    public RevocationChecker(@Value("${ENDPOINT_SERVICE_URL:http://endpoint-service:8081}") String endpointServiceUrl) {
        this.client = RestClient.builder().baseUrl(endpointServiceUrl).build();
    }

    public boolean isRevoked(UUID endpointId) {
        CachedEntry cached = cache.get(endpointId);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt.isAfter(now)) {
            return cached.revoked;
        }
        boolean revoked = fetch(endpointId);
        cache.put(endpointId, new CachedEntry(revoked, now.plus(CACHE_TTL)));
        return revoked;
    }

    private boolean fetch(UUID endpointId) {
        try {
            Map<?, ?> resp = client.get()
                .uri("/internal/endpoints/{id}/revoked", endpointId)
                .retrieve()
                .body(Map.class);
            return resp != null && Boolean.TRUE.equals(resp.get("revoked"));
        } catch (Exception e) {
            logger.warn("revocation check failed for {}: {} — fail-open", endpointId, e.getMessage());
            return false;
        }
    }

    private record CachedEntry(boolean revoked, Instant expiresAt) {}
}
