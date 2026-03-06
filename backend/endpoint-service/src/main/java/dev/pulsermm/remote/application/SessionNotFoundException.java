package dev.pulsermm.remote.application;

import java.util.UUID;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(UUID id) {
        super("Desktop session not found: " + id);
    }
}
