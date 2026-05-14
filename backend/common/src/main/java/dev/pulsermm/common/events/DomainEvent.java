package dev.pulsermm.common.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DomainEvent(
    UUID id,
    String type,
    Instant occurredAt,
    Map<String, Object> data
) {
    public static DomainEvent of(String type, Map<String, Object> data) {
        return new DomainEvent(UUID.randomUUID(), type, Instant.now(), data);
    }
}
