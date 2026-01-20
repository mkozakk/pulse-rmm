package dev.pulsermm.enrolment.api;

import dev.pulsermm.enrolment.application.TokenService;
import dev.pulsermm.enrolment.domain.Endpoint;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class EnrolmentController {
    private final TokenService tokenService;
    private final EndpointRepository endpointRepository;

    public EnrolmentController(TokenService tokenService, EndpointRepository endpointRepository) {
        this.tokenService = tokenService;
        this.endpointRepository = endpointRepository;
    }

    @PostMapping("/api/enrolment/tokens")
    public ResponseEntity<TokenResponse> createToken(
            @Valid @RequestBody CreateTokenRequest request,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var token = tokenService.createToken(request.groupId(), request.ttlHours());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new TokenResponse(token.getId(), token.getExpiresAt()));
    }

    @GetMapping("/api/endpoints")
    public ResponseEntity<List<EndpointResponse>> listEndpoints(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<EndpointResponse> endpoints = endpointRepository.findAll()
            .stream()
            .map(EndpointResponse::from)
            .toList();

        return ResponseEntity.ok(endpoints);
    }
}
