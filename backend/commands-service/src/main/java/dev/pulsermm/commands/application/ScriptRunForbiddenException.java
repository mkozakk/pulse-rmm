package dev.pulsermm.commands.application;

public class ScriptRunForbiddenException extends RuntimeException {
    public ScriptRunForbiddenException(String message) {
        super(message);
    }
}
