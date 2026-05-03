package dev.pulsermm.script.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
public class GatewayClient {

    private final RestClient restClient;

    public GatewayClient(@Value("${pulse.gateway.url:http://localhost:8080}") String gatewayUrl) {
        this.restClient = RestClient.builder()
            .baseUrl(gatewayUrl)
            .build();
    }

    public void dispatchScriptCommand(UUID endpointId, UUID runResultId, String scriptBody, Map<String, String> envVars, String callbackUrl) {
        try {
            var request = new DispatchRequest(endpointId, runResultId.toString(), scriptBody, envVars, callbackUrl);
            restClient.post()
                .uri("/internal/script-commands/dispatch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            // Log but don't fail the request — agent may come online later
        }
    }

    public record DispatchRequest(UUID endpointId, String commandId, String scriptBody, Map<String, String> envVars, String callbackUrl) {}
}
