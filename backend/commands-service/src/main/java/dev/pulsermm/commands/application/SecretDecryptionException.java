package dev.pulsermm.commands.application;

public class SecretDecryptionException extends RuntimeException {
    public SecretDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
