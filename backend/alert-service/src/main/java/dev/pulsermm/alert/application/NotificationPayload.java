package dev.pulsermm.alert.application;

import java.time.Instant;

public record NotificationPayload(String type, String message, Instant occurredAt) {
}
