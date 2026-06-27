package dev.pulsermm.remote.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class AgentHubClient {

    private static final Logger logger = LoggerFactory.getLogger(AgentHubClient.class);

    private final RestClient restClient;
    private final String internalSecret;

    public AgentHubClient(@Value("${pulse.agent-hub.url:http://localhost:8092}") String agentHubUrl,
                          @Value("${pulse.identity.internal-secret}") String internalSecret) {
        this.internalSecret = internalSecret;
        this.restClient = RestClient.builder().baseUrl(agentHubUrl).build();
    }

    public void startDesktopSession(UUID endpointId, UUID sessionId, List<String> turnUrls, String turnSecret) {
        try {
            var body = new StartSessionRequest(endpointId, sessionId, turnUrls, turnSecret);
            restClient.post()
                .uri("/internal/desktop-sessions/start")
                .header("X-Internal-Token", internalSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            logger.info("Dispatched StartDesktopSession for session {} to endpoint {}", sessionId, endpointId);
        } catch (Exception e) {
            logger.warn("Failed to dispatch StartDesktopSession for session {}: {}", sessionId, e.getMessage());
        }
    }

    public void endDesktopSession(UUID endpointId, UUID sessionId) {
        try {
            var body = new EndSessionRequest(endpointId, sessionId);
            restClient.post()
                .uri("/internal/desktop-sessions/end")
                .header("X-Internal-Token", internalSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            logger.warn("Failed to dispatch EndDesktopSession for session {}: {}", sessionId, e.getMessage());
        }
    }

    public record StartSessionRequest(UUID endpointId, UUID sessionId, List<String> turnUrls, String turnSecret) {}
    public record EndSessionRequest(UUID endpointId, UUID sessionId) {}
}
