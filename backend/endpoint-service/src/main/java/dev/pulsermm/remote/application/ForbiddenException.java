package dev.pulsermm.remote.application;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String permission) {
        super("missing permission: " + permission);
    }
}
