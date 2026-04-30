package dev.pulsermm.identity.application;

import dev.pulsermm.identity.api.dto.RegisterRequest;
import dev.pulsermm.identity.api.dto.RegisterResponse;
import dev.pulsermm.identity.api.errors.BootstrapClosedException;
import dev.pulsermm.identity.domain.Role;
import dev.pulsermm.identity.domain.User;
import dev.pulsermm.identity.infrastructure.RoleRepository;
import dev.pulsermm.identity.infrastructure.UserRepository;
import dev.pulsermm.identity.infrastructure.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock PasswordEncoder passwordEncoder;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, userRoleRepository, passwordEncoder, null, null, null);
    }

    @Test
    void registerHashesPasswordAndSavesUser() {
        UUID id = UUID.randomUUID();
        User saved = new User("admin", "$2a$04$hashed");
        saved.setId(id);
        Role adminRole = new Role("Admin");
        adminRole.setId(UUID.randomUUID());

        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("rawpassword12")).thenReturn("$2a$04$hashed");
        when(userRepository.save(any())).thenReturn(saved);
        when(roleRepository.findByName("Admin")).thenReturn(Optional.of(adminRole));
        when(userRoleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

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
        // roleRepository not reached — DataIntegrityViolationException thrown first

        assertThatThrownBy(() -> authService.register(new RegisterRequest("admin", "rawpassword12")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
