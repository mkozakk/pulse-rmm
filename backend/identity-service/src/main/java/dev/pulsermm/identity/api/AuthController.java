package dev.pulsermm.identity.api;

import dev.pulsermm.identity.api.dto.RegisterRequest;
import dev.pulsermm.identity.api.dto.RegisterResponse;
import dev.pulsermm.identity.application.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@RequestBody @Valid RegisterRequest request) {
        return authService.register(request);
    }
}
