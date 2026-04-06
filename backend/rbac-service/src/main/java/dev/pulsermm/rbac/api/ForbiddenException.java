package dev.pulsermm.rbac.api;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
