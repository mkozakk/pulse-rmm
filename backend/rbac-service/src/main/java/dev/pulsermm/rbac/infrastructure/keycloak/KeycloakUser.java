package dev.pulsermm.rbac.infrastructure.keycloak;

import java.util.UUID;

public record KeycloakUser(UUID id, String username, String email, String firstName, String lastName, boolean enabled) {}
