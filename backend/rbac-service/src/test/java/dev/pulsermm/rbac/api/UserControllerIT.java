package dev.pulsermm.rbac.api;

import dev.pulsermm.rbac.api.dto.ResolvedPermission;
import dev.pulsermm.rbac.application.PermissionEvaluationService;
import dev.pulsermm.rbac.infrastructure.keycloak.KeycloakAdminClient;
import dev.pulsermm.rbac.infrastructure.keycloak.KeycloakUser;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class UserControllerIT {

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

    @Autowired
    MockMvc mvc;

    @MockBean
    KeycloakAdminClient keycloakAdminClient;

    @MockBean
    PermissionEvaluationService permissionEvaluationService;

    private static final UUID ADMIN_USER = UUID.randomUUID();
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private void givenAdminPermissions() {
        when(permissionEvaluationService.resolve(ADMIN_USER))
            .thenReturn(Set.of(new ResolvedPermission("identity:user:manage", null)));
    }

    @Test
    void listUsersRequiresAuth() throws Exception {
        mvc.perform(get("/api/identity/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsersReturnsForbiddenWithoutPermission() throws Exception {
        when(permissionEvaluationService.resolve(ADMIN_USER)).thenReturn(Set.of());

        mvc.perform(get("/api/identity/users")
                .with(jwt().jwt(j -> j.subject(ADMIN_USER.toString()))))
            .andExpect(status().isForbidden());
    }

    @Test
    void listUsersReturnsOk() throws Exception {
        givenAdminPermissions();
        var user = new KeycloakUser(USER_ID, "alice", "alice@test.com", "Alice", "Smith", true);
        when(keycloakAdminClient.listUsers()).thenReturn(List.of(user));

        mvc.perform(get("/api/identity/users")
                .with(jwt().jwt(j -> j.subject(ADMIN_USER.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(USER_ID.toString()))
            .andExpect(jsonPath("$[0].username").value("alice"));
    }

    @Test
    void createUserReturns201() throws Exception {
        givenAdminPermissions();
        var user = new KeycloakUser(USER_ID, "bob", "bob@test.com", "Bob", "Builder", true);
        when(keycloakAdminClient.createUser(any(), any(), any(), any(), any())).thenReturn(USER_ID);
        when(keycloakAdminClient.getUser(USER_ID)).thenReturn(user);

        mvc.perform(post("/api/identity/users")
                .contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(j -> j.subject(ADMIN_USER.toString())))
                .content("""
                    {"username":"bob","email":"bob@test.com","firstName":"Bob","lastName":"Builder","password":"secret123"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(USER_ID.toString()));
    }

    @Test
    void createUserMissingPasswordReturns400() throws Exception {
        givenAdminPermissions();

        mvc.perform(post("/api/identity/users")
                .contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(j -> j.subject(ADMIN_USER.toString())))
                .content("""
                    {"username":"bob","email":"bob@test.com","firstName":"Bob","lastName":"Builder"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteUserReturns204() throws Exception {
        givenAdminPermissions();

        mvc.perform(delete("/api/identity/users/{id}", USER_ID)
                .with(jwt().jwt(j -> j.subject(ADMIN_USER.toString()))))
            .andExpect(status().isNoContent());
    }

    @Test
    void updateUserRolesReturns200() throws Exception {
        givenAdminPermissions();

        mvc.perform(put("/api/identity/users/{id}/roles", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(j -> j.subject(ADMIN_USER.toString())))
                .content("{\"roleIds\":[]}"))
            .andExpect(status().isOk());
    }
}
