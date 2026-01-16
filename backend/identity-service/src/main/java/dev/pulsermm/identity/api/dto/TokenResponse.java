package dev.pulsermm.identity.api.dto;

public record TokenResponse(String accessToken, long expiresIn) {}
