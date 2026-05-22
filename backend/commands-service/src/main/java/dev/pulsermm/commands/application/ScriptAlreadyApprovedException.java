package dev.pulsermm.commands.application;

public class ScriptAlreadyApprovedException extends RuntimeException {
    public ScriptAlreadyApprovedException(String message) {
        super(message);
    }
}
