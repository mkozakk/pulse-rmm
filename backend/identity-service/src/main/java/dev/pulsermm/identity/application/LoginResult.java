package dev.pulsermm.identity.application;

import dev.pulsermm.identity.api.dto.TokenResponse;
import org.springframework.http.ResponseCookie;

public record LoginResult(TokenResponse body, ResponseCookie cookie) {}
