package dev.pulsermm.gateway.infrastructure.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

@Component
public class EndpointOrgClient {

    private final RestClient restClient;
    private final String internalSecret;

    public EndpointOrgClient(@Value("${pulse.endpoint.url:http://endpoint-service:8081}") String endpointUrl,
                             @Value("${pulse.identity.internal-secret}") String internalSecret) {
        this.internalSecret = internalSecret;
        this.restClient = RestClient.builder().baseUrl(endpointUrl).build();
    }

    // Returns the endpoint's owning org, or empty when the endpoint has no org (global/unassigned) or is unknown.
    public Optional<UUID> getEndpointOrg(String endpointId) {
        try {
            var response = restClient.get()
                .uri("/internal/endpoints/{id}/org", endpointId)
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
