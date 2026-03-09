package dev.pulsermm.integration.application;

import dev.pulsermm.integration.domain.Webhook;
import dev.pulsermm.integration.infrastructure.persistence.WebhookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

class WebhookServiceTest {

    private WebhookRepository repository;
    private WebhookSecretEncryptor encryptor;
    private WebhookService service;

    @BeforeEach
    void setUp() {
        repository = mock(WebhookRepository.class);
        encryptor = mock(WebhookSecretEncryptor.class);
        service = new WebhookService(repository, encryptor);
    }

    @Test
    void createValidatesHttpsUrl() {
        when(encryptor.encrypt("secret")).thenReturn(new byte[0]);
        when(repository.save(any())).thenReturn(new Webhook("https://example.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID()));

        service.create("https://example.com", List.of("alert.fired"), "secret", UUID.randomUUID());

        verify(repository).save(any());
    }

    @Test
    void createValidatesHttpUrl() {
        when(encryptor.encrypt("secret")).thenReturn(new byte[0]);
        when(repository.save(any())).thenReturn(new Webhook("http://example.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID()));

        service.create("http://example.com", List.of("alert.fired"), "secret", UUID.randomUUID());

        verify(repository).save(any());
    }

    @Test
    void createRejectsInvalidUrl() {
        assertThatThrownBy(() -> service.create("ftp://example.com", List.of("alert.fired"), "secret", UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsEmptyEventTypes() {
        assertThatThrownBy(() -> service.create("https://example.com", List.of(), "secret", UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createAcceptsKnownEventTypes() {
        when(encryptor.encrypt("secret")).thenReturn(new byte[0]);
        when(repository.save(any())).thenReturn(new Webhook("https://example.com", new byte[0], "kek-1", List.of("alert.fired", "endpoint.online"), UUID.randomUUID()));

        service.create("https://example.com", List.of("alert.fired", "endpoint.online"), "secret", UUID.randomUUID());

        verify(repository).save(any());
    }

    @Test
    void createAcceptsCustomAuditEventTypes() {
        when(encryptor.encrypt("secret")).thenReturn(new byte[0]);
        when(repository.save(any())).thenReturn(new Webhook("https://example.com", new byte[0], "kek-1", List.of("audit.user.login"), UUID.randomUUID()));

        service.create("https://example.com", List.of("audit.user.login"), "secret", UUID.randomUUID());

        verify(repository).save(any());
    }

    @Test
    void createRejectsInvalidEventType() {
        assertThatThrownBy(() -> service.create("https://example.com", List.of("unknown.type"), "secret", UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void listReturnsAllWebhooks() {
        var webhook1 = new Webhook("https://example1.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID());
        var webhook2 = new Webhook("https://example2.com", new byte[0], "kek-1", List.of("endpoint.online"), UUID.randomUUID());
        when(repository.findAll()).thenReturn(List.of(webhook1, webhook2));

        var result = service.list();

        assertThat(result).hasSize(2);
    }

    @Test
    void getThrowsIfNotFound() {
        UUID webhookId = UUID.randomUUID();
        when(repository.findById(webhookId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(webhookId))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getReturnsWebhookIfFound() {
        UUID webhookId = UUID.randomUUID();
        var webhook = new Webhook("https://example.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID());
        when(repository.findById(webhookId)).thenReturn(Optional.of(webhook));

        var result = service.get(webhookId);

        assertThat(result).isEqualTo(webhook);
    }

    @Test
    void updatePartialWorks() {
        UUID webhookId = UUID.randomUUID();
        var webhook = new Webhook("https://example.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID());
        when(repository.findById(webhookId)).thenReturn(Optional.of(webhook));
        when(repository.save(webhook)).thenReturn(webhook);

        service.update(webhookId, null, null, false, null);

        assertThat(webhook.isEnabled()).isFalse();
    }

    @Test
    void deleteRemovesWebhook() {
        UUID webhookId = UUID.randomUUID();
        var webhook = new Webhook("https://example.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID());
        when(repository.findById(webhookId)).thenReturn(Optional.of(webhook));

        service.delete(webhookId);

        verify(repository).delete(webhook);
    }
}
