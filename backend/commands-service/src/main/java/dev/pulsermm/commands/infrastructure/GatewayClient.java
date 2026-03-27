package dev.pulsermm.commands.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
public class GatewayClient {

    private static final Logger logger = LoggerFactory.getLogger(GatewayClient.class);
    private final RestClient restClient;

    public GatewayClient(@Value("${pulse.gateway.url:http://localhost:8080}") String gatewayUrl) {
        this.restClient = RestClient.builder()
            .baseUrl(gatewayUrl)
            .build();
    }

    public void dispatchScriptCommand(UUID endpointId, UUID runResultId, String scriptBody, Map<String, String> envVars, String callbackUrl) {
        try {
            var request = new DispatchRequest(endpointId, runResultId.toString(), scriptBody, envVars, callbackUrl);
            logger.info("Dispatching script command {} to endpoint {} via gateway", runResultId, endpointId);
            restClient.post()
                .uri("/internal/script-commands/dispatch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
            logger.info("Script command dispatched successfully");
        } catch (Exception e) {
            logger.error("Failed to dispatch script command to gateway: {}", e.getMessage(), e);
        }
    }

    public void dispatchSoftwareCommand(UUID endpointId, String commandId, String action, String packageName, String appId, String packageVersion) {
        try {
            var request = new SoftwareDispatchRequest(endpointId, commandId, action, packageName, appId, packageVersion);
            logger.info("Dispatching software command {} to endpoint {} via gateway", commandId, endpointId);
            restClient.post()
                .uri("/internal/software-commands/dispatch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
            logger.info("Software command dispatched successfully");
        } catch (Exception e) {
            logger.error("Failed to dispatch software command to gateway: {}", e.getMessage(), e);
        }
    }

    public void dispatchListProcesses(UUID endpointId, String commandId, String callbackUrl) {
        try {
            var request = new ProcessDispatchRequest(endpointId, commandId, callbackUrl, 0);
            logger.info("Dispatching list-processes command {} to endpoint {}", commandId, endpointId);
            restClient.post()
                .uri("/internal/process-commands/list/dispatch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            logger.error("Failed to dispatch list-processes command to gateway: {}", e.getMessage(), e);
        }
    }

    public void dispatchKillProcess(UUID endpointId, String commandId, int pid, String callbackUrl) {
        try {
            var request = new ProcessDispatchRequest(endpointId, commandId, callbackUrl, pid);
            logger.info("Dispatching kill-process pid={} command {} to endpoint {}", pid, commandId, endpointId);
            restClient.post()
                .uri("/internal/process-commands/kill/dispatch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            logger.error("Failed to dispatch kill-process command to gateway: {}", e.getMessage(), e);
        }
    }

    public record DispatchRequest(UUID endpointId, String commandId, String scriptBody, Map<String, String> envVars, String callbackUrl) {}

    public record SoftwareDispatchRequest(UUID endpointId, String commandId, String action, String packageName, String appId, String packageVersion) {}

    public record ProcessDispatchRequest(UUID endpointId, String commandId, String callbackUrl, int pid) {}
}
