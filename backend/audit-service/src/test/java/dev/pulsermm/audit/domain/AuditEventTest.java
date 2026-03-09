package dev.pulsermm.audit.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventTest {

    @Test
    void creationStoresAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        Instant now = Instant.now();
        var event = new AuditEvent(id, userId, "alice", "auth:login", "user.login", endpointId, "{}", now);

        assertThat(event.getId()).isEqualTo(id);
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getUsername()).isEqualTo("alice");
        assertThat(event.getPermissionUsed()).isEqualTo("auth:login");
        assertThat(event.getAction()).isEqualTo("user.login");
        assertThat(event.getEndpointId()).isEqualTo(endpointId);
        assertThat(event.getPayload()).isEqualTo("{}");
        assertThat(event.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void nullValuesAllowed() {
        var event = new AuditEvent(UUID.randomUUID(), null, null, "policy:read", "policy.view", null, null, Instant.now());

        assertThat(event.getUserId()).isNull();
        assertThat(event.getUsername()).isNull();
        assertThat(event.getEndpointId()).isNull();
        assertThat(event.getPayload()).isNull();
    }

    @Test
    void payloadCanBeJson() {
        var json = "{\"user_id\":\"123\",\"action\":\"login\"}";
        var event = new AuditEvent(UUID.randomUUID(), UUID.randomUUID(), "bob", "auth:login", "user.login", null, json, Instant.now());

        assertThat(event.getPayload()).isEqualTo(json);
        assertThat(event.getPayload()).contains("user_id");
    }

}
