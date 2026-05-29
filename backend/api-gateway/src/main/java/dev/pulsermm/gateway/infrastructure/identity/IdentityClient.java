package dev.pulsermm.gateway.infrastructure.identity;

import dev.pulsermm.gateway.api.ResolvedPermission;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class IdentityClient {

    private final RestClient restClient;
    private final String internalSecret;

    public IdentityClient(@Value("${pulse.identity.url:http://rbac-service:8083}") String identityUrl,
                         @Value("${pulse.identity.internal-secret}") String internalSecret) {
        this.internalSecret = internalSecret;
        this.restClient = RestClient.builder()
            .baseUrl(identityUrl)
            .build();
    }

    public List<ResolvedPermission> getPermissions(String userId) {
        try {
            var response = restClient.get()
                .uri("/internal/rbac/permissions/{userId}", userId)
                .header("X-Internal-Token", internalSecret)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ResolvedPermission[].class);
            return response != null ? List.of(response) : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public Optional<UUID> getEndpointGroup(String endpointId) {
        try {
            var response = restClient.get()
                .uri("/internal/rbac/endpoint-groups/{endpointId}", endpointId)
                .header("X-Internal-Token", internalSecret)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(UUID.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
