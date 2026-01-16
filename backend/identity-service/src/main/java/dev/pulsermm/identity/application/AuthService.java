package dev.pulsermm.identity.application;

import dev.pulsermm.identity.api.dto.RegisterRequest;
import dev.pulsermm.identity.api.dto.RegisterResponse;
import dev.pulsermm.identity.api.errors.BootstrapClosedException;
import dev.pulsermm.identity.domain.User;
import dev.pulsermm.identity.infrastructure.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
}
