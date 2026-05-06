package dev.pulsermm.script.api;

public record CommandAckRequest(
        Integer exitCode,
        String output
) {
}
