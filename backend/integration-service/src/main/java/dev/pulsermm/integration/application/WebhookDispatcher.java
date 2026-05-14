package dev.pulsermm.integration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.integration.domain.WebhookDelivery;
import dev.pulsermm.integration.infrastructure.persistence.WebhookDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private static final int MAX_ATTEMPTS = 3;

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSecretEncryptor encryptor;
    private final HmacSigner hmacSigner;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WebhookDispatcher(WebhookDeliveryRepository deliveryRepository,
                              WebhookSecretEncryptor encryptor,
                              HmacSigner hmacSigner,
                              RestClient webhookRestClient,
                              ObjectMapper objectMapper) {
        this.deliveryRepository = deliveryRepository;
        this.encryptor = encryptor;
        this.hmacSigner = hmacSigner;
        this.restClient = webhookRestClient;
        this.objectMapper = objectMapper;
    }

    public void dispatch(WebhookDelivery delivery) {
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setStatus("retrying");
        deliveryRepository.save(delivery);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(delivery.getPayload());
        } catch (Exception e) {
            log.error("Failed to serialize payload for delivery={}", delivery.getId());
            markDead(delivery, "serialization error: " + e.getMessage(), null);
            return;
        }

        var secret = encryptor.decrypt(delivery.getWebhook().getSecretCiphertext());
        var signature = hmacSigner.sign(body, secret.getBytes(StandardCharsets.UTF_8));
        var deliveryId = delivery.getId().toString();
        var eventType = delivery.getEventType();
        var url = delivery.getWebhook().getUrl();

        log.info("dispatch delivery={} webhook={} event={} attempt={}",
                deliveryId, delivery.getWebhook().getId(), eventType, delivery.getAttempts());

        try {
            var response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Pulse-Signature", signature)
                    .header("X-Pulse-Event", eventType)
                    .header("X-Pulse-Delivery", deliveryId)
                    .header("User-Agent", "PulseRMM-Webhook/1.0")
                    .body(body)
                    .retrieve()
                    .onStatus(s -> s.isError(), (req, res) -> {})
                    .toBodilessEntity();

            int code = response.getStatusCode().value();
            delivery.setLastStatusCode(code);

            if (response.getStatusCode().is2xxSuccessful()) {
                delivery.setStatus("success");
                delivery.setCompletedAt(Instant.now());
                log.info("delivery success delivery={} status={}", deliveryId, code);
            } else if (shouldRetry(code) && delivery.getAttempts() < MAX_ATTEMPTS) {
                delivery.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds(delivery.getAttempts())));
                log.warn("delivery failed delivery={} status={} attempt={} retry_at={}",
                        deliveryId, code, delivery.getAttempts(), delivery.getNextRetryAt());
            } else {
                markDead(delivery, "http " + code, code);
                return;
            }
        } catch (ResourceAccessException e) {
            delivery.setLastError(e.getMessage());
            if (delivery.getAttempts() < MAX_ATTEMPTS) {
                delivery.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds(delivery.getAttempts())));
                log.warn("delivery connection error delivery={} attempt={}: {}", deliveryId, delivery.getAttempts(), e.getMessage());
            } else {
                markDead(delivery, e.getMessage(), null);
                return;
            }
        }

        deliveryRepository.save(delivery);
    }

    private void markDead(WebhookDelivery delivery, String error, Integer code) {
        delivery.setStatus("dead_letter");
        delivery.setLastError(error);
        if (code != null) delivery.setLastStatusCode(code);
        delivery.setCompletedAt(Instant.now());
        deliveryRepository.save(delivery);
        log.warn("delivery dead_letter delivery={} webhook={} event={} attempts={}",
                delivery.getId(), delivery.getWebhook().getId(), delivery.getEventType(), delivery.getAttempts());
    }

    private boolean shouldRetry(int code) {
        return code >= 500 || code == 408 || code == 429;
    }

    private long backoffSeconds(int attempts) {
        return (long) Math.pow(4, attempts - 1);
    }
}
