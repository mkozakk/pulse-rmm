package dev.pulsermm.enrolment.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class IdentityServiceClient {

    private final RestClient restClient;
    private final String internalSecret;

    public IdentityServiceClient(
            @Value("${pulse.identity.url}") String identityUrl,
            @Value("${pulse.identity.internal-secret}") String internalSecret) {
        this.restClient = RestClient.builder().baseUrl(identityUrl).build();
        this.internalSecret = internalSecret;
    }

    public void setEndpointGroup(UUID endpointId, UUID groupId) {
        restClient.put()
            .uri("/internal/rbac/endpoint-groups/" + endpointId)
            .header("X-Internal-Token", internalSecret)
            .contentType(MediaType.APPLICATION_JSON)
            .body(groupId)
            .retrieve()
            .toBodilessEntity();
    }
}
