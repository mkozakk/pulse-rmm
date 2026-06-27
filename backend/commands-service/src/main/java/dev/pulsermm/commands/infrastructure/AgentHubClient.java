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
public class AgentHubClient {

    private static final Logger logger = LoggerFactory.getLogger(AgentHubClient.class);
    private final RestClient restClient;
    private final String internalSecret;

    public AgentHubClient(@Value("${pulse.agent-hub.url:http://localhost:8092}") String agentHubUrl,
                          @Value("${pulse.identity.internal-secret}") String internalSecret) {
        this.internalSecret = internalSecret;
        this.restClient = RestClient.builder()
            .baseUrl(agentHubUrl)
            .build();
    }

    public void dispatchScriptCommand(UUID endpointId, UUID runResultId, String scriptBody, Map<String, String> envVars, String callbackUrl) {
        try {
            var request = new DispatchRequest(endpointId, runResultId.toString(), scriptBody, envVars, callbackUrl);
            logger.info("Dispatching script command {} to endpoint {} via agent-hub", runResultId, endpointId);
            restClient.post()
                .uri("/internal/script-commands/dispatch")
                .header("X-Internal-Token", internalSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
            logger.info("Script command dispatched successfully");
        } catch (Exception e) {
            logger.error("Failed to dispatch script command to agent-hub: {}", e.getMessage(), e);
        }
    }

    public void dispatchSoftwareCommand(UUID endpointId, String commandId, String action, String packageName, String appId, String packageVersion) {
        try {
            var request = new SoftwareDispatchRequest(endpointId, commandId, action, packageName, appId, packageVersion);
            logger.info("Dispatching software command {} to endpoint {} via agent-hub", commandId, endpointId);
            restClient.post()
                .uri("/internal/software-commands/dispatch")
                .header("X-Internal-Token", internalSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
            logger.info("Software command dispatched successfully");
        } catch (Exception e) {
            logger.error("Failed to dispatch software command to agent-hub: {}", e.getMessage(), e);
        }
    }

    public void dispatchListProcesses(UUID endpointId, String commandId, String callbackUrl) {
        try {
            var request = new ProcessDispatchRequest(endpointId, commandId, callbackUrl, 0);
            logger.info("Dispatching list-processes command {} to endpoint {}", commandId, endpointId);
            restClient.post()
                .uri("/internal/process-commands/list/dispatch")
                .header("X-Internal-Token", internalSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            logger.error("Failed to dispatch list-processes command to agent-hub: {}", e.getMessage(), e);
        }
    }

    public void dispatchKillProcess(UUID endpointId, String commandId, int pid, String callbackUrl) {
        try {
            var request = new ProcessDispatchRequest(endpointId, commandId, callbackUrl, pid);
            logger.info("Dispatching kill-process pid={} command {} to endpoint {}", pid, commandId, endpointId);
            restClient.post()
                .uri("/internal/process-commands/kill/dispatch")
                .header("X-Internal-Token", internalSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            logger.error("Failed to dispatch kill-process command to agent-hub: {}", e.getMessage(), e);
        }
    }

    public record DispatchRequest(UUID endpointId, String commandId, String scriptBody, Map<String, String> envVars, String callbackUrl) {}

    public record SoftwareDispatchRequest(UUID endpointId, String commandId, String action, String packageName, String appId, String packageVersion) {}

    public record ProcessDispatchRequest(UUID endpointId, String commandId, String callbackUrl, int pid) {}
}
