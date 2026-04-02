package dev.pulsermm.rbac.api;

import dev.pulsermm.rbac.infrastructure.UserRoleRepository;
import dev.pulsermm.rbac.infrastructure.keycloak.KeycloakAdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class InternalRbacControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("pulse")
        .withUsername("pulse")
        .withPassword("pulse");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.0-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registry.add("spring.rabbitmq.listener.direct.auto-startup", () -> "false");
        registry.add("INTERNAL_API_SECRET", () -> "test-internal-secret");
        registry.add("KEYCLOAK_ADMIN", () -> "admin");
        registry.add("KEYCLOAK_ADMIN_PASSWORD", () -> "admin");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
            () -> "http://localhost:8180/realms/pulse-rmm");
    }

    @MockBean
    KeycloakAdminClient keycloakAdminClient;

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRoleRepository userRoleRepository;

    private static final UUID NEW_USER = UUID.randomUUID();
    private static final String SECRET = "test-internal-secret";

    @Test
    void assignRoleCreatesUserRole() throws Exception {
        mvc.perform(post("/internal/rbac/users/{id}/roles", NEW_USER)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Token", SECRET)
                .content("{\"roleName\":\"Admin\"}"))
            .andExpect(status().isOk());

        var roles = userRoleRepository.findAllByIdUserId(NEW_USER);
        assertThat(roles).isNotEmpty();
    }

    @Test
    void assignRoleWithUnknownRoleReturns404() throws Exception {
        mvc.perform(post("/internal/rbac/users/{id}/roles", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Token", SECRET)
                .content("{\"roleName\":\"NoSuchRole\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void assignRoleWithoutTokenReturns403() throws Exception {
        mvc.perform(post("/internal/rbac/users/{id}/roles", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleName\":\"Admin\"}"))
            .andExpect(status().isForbidden());
    }
}
