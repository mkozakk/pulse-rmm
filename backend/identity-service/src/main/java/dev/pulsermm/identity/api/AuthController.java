package dev.pulsermm.identity.api;

import dev.pulsermm.identity.api.dto.LoginRequest;
import dev.pulsermm.identity.api.dto.RegisterRequest;
import dev.pulsermm.identity.api.dto.RegisterResponse;
import dev.pulsermm.identity.api.dto.TokenResponse;
import dev.pulsermm.identity.api.errors.InvalidRefreshTokenException;
import dev.pulsermm.identity.application.AuthService;
import dev.pulsermm.identity.application.LoginResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Login, registration, token refresh and logout")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new user")
    @ApiResponse(responseCode = "201", description = "User created",
        content = @Content(schema = @Schema(implementation = RegisterResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "409", description = "Username already taken")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@RequestBody @Valid RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(summary = "Authenticate and obtain tokens")
    @ApiResponse(responseCode = "200", description = "Access token + refresh cookie set",
        content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest request) {
        LoginResult result = authService.login(request);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, result.cookie().toString())
            .body(result.body());
    }

    @Operation(summary = "Refresh access token using cookie",
        description = "Reads the pulse_refresh HttpOnly cookie — no bearer token required")
    @ApiResponse(responseCode = "200", description = "New access token issued",
        content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "401", description = "Refresh token missing or revoked")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@CookieValue(name = "pulse_refresh", required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new InvalidRefreshTokenException();
        }
        LoginResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, result.cookie().toString())
            .body(result.body());
    }

    @Operation(summary = "Logout and invalidate refresh token")
    @ApiResponse(responseCode = "204", description = "Logged out, cookie cleared")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "pulse_refresh", required = false) String refreshToken) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, authService.clearRefreshCookie().toString())
            .build();
    }
}
