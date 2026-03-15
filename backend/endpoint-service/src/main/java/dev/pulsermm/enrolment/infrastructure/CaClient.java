package dev.pulsermm.enrolment.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "pulse.ca.enabled", havingValue = "true")
public class CaClient {

    private static final Logger logger = LoggerFactory.getLogger(CaClient.class);

    private final RestClient client;

    public CaClient(@Value("${pulse.ca.url:http://ca-service:8089}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public SignedCert sign(String csrPem, UUID endpointId) {
        try {
            Map<?, ?> body = client.post()
                .uri("/internal/ca/sign")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("csrPem", csrPem, "endpointId", endpointId.toString()))
                .retrieve()
                .body(Map.class);
            if (body == null) {
                throw new IllegalStateException("ca-service returned empty body");
            }
            return new SignedCert((String) body.get("certPem"), (String) body.get("caBundlePem"));
        } catch (Exception e) {
            logger.error("ca-service sign failed", e);
            throw new IllegalStateException("ca-service sign failed: " + e.getMessage(), e);
        }
    }

    public record SignedCert(String certPem, String caBundlePem) {}
}
