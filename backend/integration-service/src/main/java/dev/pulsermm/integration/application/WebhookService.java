package dev.pulsermm.integration.application;

import dev.pulsermm.integration.domain.Webhook;
import dev.pulsermm.integration.infrastructure.persistence.WebhookRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class WebhookService {

    private static final Set<String> VALID_EVENT_TYPES = Set.of(
        "alert.fired", "alert.acknowledged",
        "endpoint.enrolled", "endpoint.online", "endpoint.offline",
        "audit.*"
    );

    private final WebhookRepository webhookRepository;
    private final WebhookSecretEncryptor encryptor;

    public WebhookService(WebhookRepository webhookRepository, WebhookSecretEncryptor encryptor) {
        this.webhookRepository = webhookRepository;
        this.encryptor = encryptor;
    }

    @Transactional
    public Webhook create(String url, List<String> eventTypes, String secret, UUID createdBy) {
        validateUrl(url);
        validateEventTypes(eventTypes);
        var ciphertext = encryptor.encrypt(secret);
        var webhook = new Webhook(url, ciphertext, WebhookSecretEncryptor.KEK_ID, eventTypes, createdBy);
        return webhookRepository.save(webhook);
    }

    public List<Webhook> list() {
        return webhookRepository.findAll();
    }

    public Webhook get(UUID id) {
        return webhookRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "webhook not found"));
    }

    @Transactional
    public Webhook update(UUID id, String url, List<String> eventTypes, Boolean enabled, String secret) {
        var webhook = get(id);
        if (url != null) {
            validateUrl(url);
            webhook.setUrl(url);
        }
        if (eventTypes != null) {
            validateEventTypes(eventTypes);
            webhook.setEventTypes(eventTypes);
        }
        if (enabled != null) {
            webhook.setEnabled(enabled);
        }
        if (secret != null) {
            webhook.setSecretCiphertext(encryptor.encrypt(secret));
        }
        return webhookRepository.save(webhook);
    }

    @Transactional
    public void delete(UUID id) {
        var webhook = get(id);
        webhookRepository.delete(webhook);
    }

    private void validateUrl(String url) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url must be http or https");
        }
    }

    private void validateEventTypes(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one event type is required");
        }
        for (String type : eventTypes) {
            boolean valid = VALID_EVENT_TYPES.contains(type) ||
                (type.startsWith("audit.") && type.length() > 6);
            if (!valid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown event type: " + type);
            }
        }
    }
}
