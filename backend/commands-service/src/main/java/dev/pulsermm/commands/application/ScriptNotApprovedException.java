package dev.pulsermm.commands.application;

public class ScriptNotApprovedException extends RuntimeException {
    public ScriptNotApprovedException(String message) {
        super(message);
    }
}
