package dev.pulsermm.commands.processes.api.dto;

public record AckRequest(int exitCode, String output) {}
