package dev.pulsermm.software.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class GatewayClient {

    private static final Logger logger = LoggerFactory.getLogger(GatewayClient.class);

    private final RestClient restClient;

    public GatewayClient(@Value("${pulse.gateway.url:http://localhost:8080}") String gatewayUrl) {
        this.restClient = RestClient.builder()
            .baseUrl(gatewayUrl)
            .build();
        logger.info("GatewayClient initialized with URL: {}", gatewayUrl);
    }

    public void dispatchSoftwareCommand(UUID endpointId, String commandId, String action, String name, String version) {
        logger.info("Dispatching software command to gateway: endpoint={}, command={}, action={}", endpointId, commandId, action);
        try {
            var request = new DispatchRequest(endpointId, commandId, action, name, version);
            restClient.post()
                .uri("/internal/software-commands/dispatch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
            logger.info("Successfully dispatched software command {} to gateway", commandId);
        } catch (Exception e) {
            logger.error("Failed to dispatch software command {} to gateway: {}", commandId, e.getMessage(), e);
        }
    }

    public record DispatchRequest(UUID endpointId, String commandId, String action, String name, String version) {}
}
