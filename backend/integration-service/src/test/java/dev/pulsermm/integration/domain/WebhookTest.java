package dev.pulsermm.integration.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookTest {

    @Test
    void creationStoresFields() {
        UUID userId = UUID.randomUUID();
        byte[] secret = "mysecret".getBytes();
        List<String> types = List.of("alert.fired", "endpoint.online");
        var webhook = new Webhook("https://example.com/webhook", secret, "kek-1", types, userId);

        assertThat(webhook.getUrl()).isEqualTo("https://example.com/webhook");
        assertThat(webhook.getSecretCiphertext()).isEqualTo(secret);
        assertThat(webhook.getSecretKekId()).isEqualTo("kek-1");
        assertThat(webhook.getEventTypes()).containsExactlyInAnyOrder("alert.fired", "endpoint.online");
        assertThat(webhook.isEnabled()).isTrue();
        assertThat(webhook.getCreatedBy()).isEqualTo(userId);
        assertThat(webhook.getCreatedAt()).isNotNull();
    }

    @Test
    void canBeDisabled() {
        var webhook = new Webhook("https://example.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID());
        webhook.setEnabled(false);

        assertThat(webhook.isEnabled()).isFalse();
    }

    @Test
    void urlCanBeUpdated() {
        var webhook = new Webhook("https://old.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID());
        webhook.setUrl("https://new.com");

        assertThat(webhook.getUrl()).isEqualTo("https://new.com");
    }

    @Test
    void eventTypesCanBeUpdated() {
        var webhook = new Webhook("https://example.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID());
        webhook.setEventTypes(List.of("endpoint.online", "endpoint.offline"));

        assertThat(webhook.getEventTypes()).containsExactlyInAnyOrder("endpoint.online", "endpoint.offline");
    }

    @Test
    void secretCanBeUpdated() {
        byte[] oldSecret = "old".getBytes();
        byte[] newSecret = "new".getBytes();
        var webhook = new Webhook("https://example.com", oldSecret, "kek-1", List.of("alert.fired"), UUID.randomUUID());
        webhook.setSecretCiphertext(newSecret);

        assertThat(webhook.getSecretCiphertext()).isEqualTo(newSecret);
    }
}
