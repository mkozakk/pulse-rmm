package dev.pulsermm.commands.application;

public class SecretEncryptionException extends RuntimeException {
    public SecretEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
