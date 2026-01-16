package dev.pulsermm.identity.application;

import dev.pulsermm.identity.domain.User;

public record RotatedRefreshToken(User user, String rawToken) {}
