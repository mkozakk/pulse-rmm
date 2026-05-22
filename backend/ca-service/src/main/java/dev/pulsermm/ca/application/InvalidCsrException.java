package dev.pulsermm.ca.application;

public class InvalidCsrException extends RuntimeException {
    public InvalidCsrException(String message, Throwable cause) {
        super(message, cause);
    }
    public InvalidCsrException(String message) {
        super(message);
    }
}
