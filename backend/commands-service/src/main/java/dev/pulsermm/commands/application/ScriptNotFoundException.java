package dev.pulsermm.commands.application;

public class ScriptNotFoundException extends RuntimeException {
    public ScriptNotFoundException(String message) {
        super(message);
    }
}
