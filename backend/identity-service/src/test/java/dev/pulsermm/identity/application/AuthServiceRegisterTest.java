package dev.pulsermm.identity.application;

import dev.pulsermm.identity.api.dto.RegisterRequest;
import dev.pulsermm.identity.api.dto.RegisterResponse;
import dev.pulsermm.identity.api.errors.BootstrapClosedException;
import dev.pulsermm.identity.domain.User;
import dev.pulsermm.identity.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    AuthService authService;

    @Test
    void registerHashesPasswordAndSavesUser() {
        UUID id = UUID.randomUUID();
        User saved = new User("admin", "$2a$04$hashed");
        saved.setId(id);

        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("rawpassword12")).thenReturn("$2a$04$hashed");
        when(userRepository.save(any())).thenReturn(saved);

        RegisterResponse response = authService.register(new RegisterRequest("admin", "rawpassword12"));

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.username()).isEqualTo("admin");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$04$hashed");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("rawpassword12");
    }

    @Test
    void registerThrowsBootstrapClosedWhenUserExists() {
        when(userRepository.count()).thenReturn(1L);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("admin", "rawpassword12")))
            .isInstanceOf(BootstrapClosedException.class);

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void registerPropagatesDataIntegrityViolation() {
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("$2a$04$hashed");
        when(userRepository.save(any())).thenThrow(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("admin", "rawpassword12")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
