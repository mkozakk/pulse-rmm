package dev.pulsermm.commands.application;

public class ScriptRunNotFoundException extends RuntimeException {
    public ScriptRunNotFoundException(String message) {
        super(message);
    }
}
