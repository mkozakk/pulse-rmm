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
    public Webhook create(String url, List<String> eventTypes, String secret, UUID createdBy, UUID orgId) {
        validateUrl(url);
        validateEventTypes(eventTypes);
        var ciphertext = encryptor.encrypt(secret);
        var webhook = new Webhook(url, ciphertext, WebhookSecretEncryptor.KEK_ID, eventTypes, createdBy, orgId);
        return webhookRepository.save(webhook);
    }

    @Transactional
    public Webhook create(String url, List<String> eventTypes, String secret, UUID createdBy) {
        return create(url, eventTypes, secret, createdBy, null);
    }

    public List<Webhook> list(UUID orgId) {
        if (orgId == null) return webhookRepository.findAll();
        return webhookRepository.findByOrgId(orgId);
    }

    public List<Webhook> list() {
        return list(null);
    }

    public Webhook get(UUID id, UUID callerOrgId) {
        var webhook = webhookRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "webhook not found"));
        if (callerOrgId != null && !callerOrgId.equals(webhook.getOrgId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "webhook not found");
        }
        return webhook;
    }

    public Webhook get(UUID id) {
        return get(id, null);
    }

    @Transactional
    public Webhook update(UUID id, String url, List<String> eventTypes, Boolean enabled, String secret,
                          UUID callerOrgId) {
        var webhook = get(id, callerOrgId);
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
    public Webhook update(UUID id, String url, List<String> eventTypes, Boolean enabled, String secret) {
        return update(id, url, eventTypes, enabled, secret, null);
    }

    @Transactional
    public void delete(UUID id, UUID callerOrgId) {
        var webhook = get(id, callerOrgId);
        webhookRepository.delete(webhook);
    }

    @Transactional
    public void delete(UUID id) {
        delete(id, null);
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
