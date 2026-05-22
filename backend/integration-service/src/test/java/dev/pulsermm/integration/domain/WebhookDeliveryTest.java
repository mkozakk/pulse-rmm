package dev.pulsermm.integration.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookDeliveryTest {

    @Test
    void creationSetsInitialState() {
        var webhook = new Webhook("https://example.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("alert_id", (Object) "123", "status", (Object) "triggered");
        var delivery = new WebhookDelivery(webhook, "alert.fired", eventId, payload);

        assertThat(delivery.getWebhook()).isEqualTo(webhook);
        assertThat(delivery.getEventType()).isEqualTo("alert.fired");
        assertThat(delivery.getEventId()).isEqualTo(eventId);
        assertThat(delivery.getPayload()).isEqualTo(payload);
        assertThat(delivery.getStatus()).isEqualTo("pending");
        assertThat(delivery.getAttempts()).isEqualTo(0);
        assertThat(delivery.getCreatedAt()).isNotNull();
    }

    @Test
    void statusCanBeUpdated() {
        var webhook = new Webhook("https://example.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID());
        var delivery = new WebhookDelivery(webhook, "alert.fired", UUID.randomUUID(), Map.of());
        delivery.setStatus("failed");

        assertThat(delivery.getStatus()).isEqualTo("failed");
    }

    @Test
    void attemptsCanBeIncremented() {
        var webhook = new Webhook("https://example.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID());
        var delivery = new WebhookDelivery(webhook, "alert.fired", UUID.randomUUID(), Map.of());
        delivery.setAttempts(3);

        assertThat(delivery.getAttempts()).isEqualTo(3);
    }

    @Test
    void lastStatusCodeCanBeSet() {
        var webhook = new Webhook("https://example.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID());
        var delivery = new WebhookDelivery(webhook, "alert.fired", UUID.randomUUID(), Map.of());
        delivery.setLastStatusCode(500);

        assertThat(delivery.getLastStatusCode()).isEqualTo(500);
    }

    @Test
    void lastErrorCanBeSet() {
        var webhook = new Webhook("https://example.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID());
        var delivery = new WebhookDelivery(webhook, "alert.fired", UUID.randomUUID(), Map.of());
        delivery.setLastError("Connection timeout");

        assertThat(delivery.getLastError()).isEqualTo("Connection timeout");
    }

    @Test
    void nextRetryCanBeSet() {
        var webhook = new Webhook("https://example.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID());
        var delivery = new WebhookDelivery(webhook, "alert.fired", UUID.randomUUID(), Map.of());
        Instant retry = Instant.now().plusSeconds(3600);
        delivery.setNextRetryAt(retry);

        assertThat(delivery.getNextRetryAt()).isEqualTo(retry);
    }

    @Test
    void completionCanBeMarked() {
        var webhook = new Webhook("https://example.com", new byte[0], "kek-1", List.of("alert.fired"), UUID.randomUUID());
        var delivery = new WebhookDelivery(webhook, "alert.fired", UUID.randomUUID(), Map.of());
        Instant completed = Instant.now();
        delivery.setCompletedAt(completed);

        assertThat(delivery.getCompletedAt()).isEqualTo(completed);
    }
}
