package dev.pulsermm.identity.application;

import dev.pulsermm.identity.api.dto.RegisterRequest;
import dev.pulsermm.identity.infrastructure.UserRepository;
import dev.pulsermm.identity.infrastructure.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Disabled("Requires Docker/Podman - tested via e2e")
class PermissionCacheIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired PermissionEvaluationService service;
    @Autowired AuthService authService;
    @Autowired UserRoleRepository userRoleRepository;
    @Autowired UserRepository userRepository;
    @Autowired StringRedisTemplate redisTemplate;

    private UUID userId;

    @BeforeEach
    void setup() {
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("perm:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);

        var response = authService.register(new RegisterRequest("admin", "validpassword12"));
        userId = response.id();
    }

    @Test
    void cachedResultReturnedOnSecondCall() {
        var first = service.resolve(userId);
        assertThat(first).isNotEmpty();

        userRoleRepository.deleteAll();

        var second = service.resolve(userId);
        assertThat(second).hasSameElementsAs(first);
    }

    @Test
    void invalidateClearsCache() {
        service.resolve(userId);
        userRoleRepository.deleteAll();

        service.invalidate(userId);

        var afterInvalidate = service.resolve(userId);
        assertThat(afterInvalidate).isEmpty();
    }
}
