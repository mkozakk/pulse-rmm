package dev.pulsermm.software.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class GatewayClient {

    private final RestClient restClient;

    public GatewayClient(@Value("${pulse.gateway.url:http://localhost:8080}") String gatewayUrl) {
        this.restClient = RestClient.builder()
            .baseUrl(gatewayUrl)
            .build();
    }

    public void dispatchSoftwareCommand(UUID endpointId, String commandId, String action, String name, String version) {
        try {
            var request = new DispatchRequest(endpointId, commandId, action, name, version);
            restClient.post()
                .uri("/internal/software-commands/dispatch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            // Log but don't fail the request
        }
    }

    public record DispatchRequest(UUID endpointId, String commandId, String action, String name, String version) {}
}
