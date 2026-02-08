package dev.pulsermm.script.application;

public class ScriptAlreadyApprovedException extends RuntimeException {
    public ScriptAlreadyApprovedException(String message) {
        super(message);
    }
}
