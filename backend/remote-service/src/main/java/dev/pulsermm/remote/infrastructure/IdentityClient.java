package dev.pulsermm.remote.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class IdentityClient {

    private static final Logger logger = LoggerFactory.getLogger(IdentityClient.class);

    private final RestClient restClient;
    private final String internalSecret;

    public IdentityClient(
            @Value("${pulse.identity.url:http://localhost:8083}") String identityUrl,
            @Value("${pulse.identity.internal-secret}") String internalSecret) {
        this.restClient = RestClient.builder().baseUrl(identityUrl).build();
        this.internalSecret = internalSecret;
    }

    public boolean hasPermission(UUID userId, String permission) {
        try {
            List<?> permissions = restClient.get()
                .uri("/internal/rbac/permissions/{userId}", userId)
                .header("X-Internal-Token", internalSecret)
                .retrieve()
                .body(List.class);
            if (permissions == null) return false;
            return permissions.stream()
                .filter(p -> p instanceof Map<?, ?>)
                .anyMatch(p -> permission.equals(((Map<?, ?>) p).get("name")));
        } catch (Exception e) {
            logger.warn("Failed to fetch permissions for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
}
