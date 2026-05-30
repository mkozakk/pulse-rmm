package dev.pulsermm.rbac.api.dto;

public record UpdateUserRequest(String email, String firstName, String lastName, Boolean enabled, String newPassword) {}
