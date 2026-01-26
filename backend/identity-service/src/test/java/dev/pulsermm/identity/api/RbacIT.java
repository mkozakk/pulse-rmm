package dev.pulsermm.identity.api;

import dev.pulsermm.identity.api.dto.RegisterRequest;
import dev.pulsermm.identity.infrastructure.PermissionRepository;
import dev.pulsermm.identity.infrastructure.RoleRepository;
import dev.pulsermm.identity.infrastructure.UserRepository;
import dev.pulsermm.identity.infrastructure.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RbacIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository userRoleRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PermissionRepository permissionRepository;

    private String adminToken;
    private String adminUserId;

    @BeforeEach
    void setup() {
        userRoleRepository.deleteAll();
        userRepository.deleteAll();

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        var registerResp = rest.postForEntity("/api/auth/register",
            new HttpEntity<>(Map.of("username", "admin", "password", "validpassword12"), h), Map.class);
        adminUserId = (String) registerResp.getBody().get("id");

        var loginResp = rest.postForEntity("/api/auth/login",
            new HttpEntity<>(Map.of("username", "admin", "password", "validpassword12"), h), Map.class);
        adminToken = (String) loginResp.getBody().get("accessToken");
    }

    @Test
    void adminUserHasAllPermissions() {
        var user = userRepository.findById(UUID.fromString(adminUserId)).orElseThrow();
        var userRoles = userRoleRepository.findAllByIdUserId(user.getId());
        assertThat(userRoles).isNotEmpty();

        var adminRole = roleRepository.findByName("Admin").orElseThrow();
        assertThat(userRoles).anyMatch(ur -> ur.getId().getRoleId().equals(adminRole.getId()));
    }

    @Test
    void adminCanListPermissions() {
        var response = rest.exchange("/api/identity/rbac/permissions",
            HttpMethod.GET,
            withToken(adminToken),
            List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(30);
    }

    @Test
    void adminCanListRoles() {
        var response = rest.exchange("/api/identity/rbac/roles",
            HttpMethod.GET,
            withToken(adminToken),
            List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void adminCanCreateRole() {
        var response = rest.postForEntity("/api/identity/rbac/roles",
            json(Map.of("name", "CustomRole")), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
    }

    @Test
    void unauthorizedUserCannotAccessRbacApi() {
        var response = rest.exchange("/api/identity/rbac/roles",
            HttpMethod.GET,
            withToken("invalid-token"),
            Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private HttpEntity<Object> json(Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(adminToken);
        return new HttpEntity<>(body, h);
    }

    private HttpEntity<Void> withToken(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(h);
    }
}
