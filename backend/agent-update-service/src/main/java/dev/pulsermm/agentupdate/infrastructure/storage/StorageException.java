package dev.pulsermm.agentupdate.infrastructure.storage;

public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
