package dev.pulsermm.rbac.api.dto;

import java.util.List;
import java.util.UUID;

public record UserResponse(UUID id, String username, String email, String firstName, String lastName,
                           boolean enabled, List<String> roles) {}
