package dev.pulsermm.script.application;

public class ScriptNotFoundException extends RuntimeException {
    public ScriptNotFoundException(String message) {
        super(message);
    }
}
