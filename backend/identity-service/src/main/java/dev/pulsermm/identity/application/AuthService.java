package dev.pulsermm.identity.application;

import dev.pulsermm.identity.api.dto.LoginRequest;
import dev.pulsermm.identity.api.dto.RegisterRequest;
import dev.pulsermm.identity.api.dto.RegisterResponse;
import dev.pulsermm.identity.api.dto.TokenResponse;
import dev.pulsermm.identity.api.errors.BootstrapClosedException;
import dev.pulsermm.identity.api.errors.InvalidCredentialsException;
import dev.pulsermm.identity.domain.User;
import dev.pulsermm.identity.infrastructure.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseCookie;

import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties props;

    private String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokenService,
                       JwtProperties props) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.props = props;
    }

    @PostConstruct
    void init() {
        dummyHash = passwordEncoder.encode("_dummy_timing_token_");
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.count() > 0) {
            throw new BootstrapClosedException();
        }
        // count()==0 is not atomic; the username unique constraint catches concurrent inserts and GlobalExceptionHandler maps it to 409.
        String hash = passwordEncoder.encode(request.password());
        User user = userRepository.save(new User(request.username(), hash));
        return new RegisterResponse(user.getId(), user.getUsername());
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByUsername(request.username());
        if (userOpt.isEmpty()) {
            // timing-attack mitigation: run bcrypt even for unknown usernames so wall-time is similar to wrong-password path
            passwordEncoder.matches(request.password(), dummyHash);
            throw new InvalidCredentialsException();
        }
        User user = userOpt.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String raw = refreshTokenService.generate();
        refreshTokenService.issue(user, raw);
        String accessToken = jwtService.issue(user.getId(), List.of("ADMIN"));
        return new LoginResult(
            new TokenResponse(accessToken, props.accessTtl().toSeconds()),
            refreshTokenService.buildCookie(raw)
        );
    }

    @Transactional
    public LoginResult refresh(String rawToken) {
        RotatedRefreshToken rotated = refreshTokenService.rotate(rawToken);
        String accessToken = jwtService.issue(rotated.user().getId(), List.of("ADMIN"));
        return new LoginResult(
            new TokenResponse(accessToken, props.accessTtl().toSeconds()),
            refreshTokenService.buildCookie(rotated.rawToken())
        );
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokenService.revoke(rawToken);
    }

    public ResponseCookie clearRefreshCookie() {
        return refreshTokenService.clearCookie();
    }
}
