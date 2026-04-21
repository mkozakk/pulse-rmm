package dev.pulsermm.identity.api.errors;

public class BootstrapClosedException extends RuntimeException {
    public BootstrapClosedException() {
        super("Registration is disabled — a user already exists.");
    }
}
