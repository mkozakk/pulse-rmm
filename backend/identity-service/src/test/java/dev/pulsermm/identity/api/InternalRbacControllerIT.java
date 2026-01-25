package dev.pulsermm.identity.api;

import dev.pulsermm.identity.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InternalRbacControllerIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
    }

    private HttpEntity<Void> withToken(String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.set("X-Internal-Token", token);
        return new HttpEntity<>(h);
    }

    private String registerAdmin() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        var response = rest.postForEntity("/api/auth/register",
            new HttpEntity<>(Map.of("username", "admin", "password", "validpassword12"), h), Map.class);
        return (String) response.getBody().get("id");
    }

    @Test
    void returnsPermissionsForAdminUser() {
        String userId = registerAdmin();

        var response = rest.exchange(
            "/internal/rbac/permissions/" + userId,
            HttpMethod.GET, withToken("test-internal-secret"), List.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void adminPermissionsIncludeEndpointView() {
        String userId = registerAdmin();

        var response = rest.exchange(
            "/internal/rbac/permissions/" + userId,
            HttpMethod.GET, withToken("test-internal-secret"), List.class);

        assertThat(response.getBody().toString()).contains("endpoint:view");
    }

    @Test
    void missingTokenReturnsForbidden() {
        String userId = registerAdmin();

        var response = rest.exchange(
            "/internal/rbac/permissions/" + userId,
            HttpMethod.GET, withToken(null), List.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void wrongTokenReturnsForbidden() {
        String userId = registerAdmin();

        var response = rest.exchange(
            "/internal/rbac/permissions/" + userId,
            HttpMethod.GET, withToken("wrong-secret"), List.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void unknownUserIdReturns404() {
        var response = rest.exchange(
            "/internal/rbac/permissions/00000000-0000-0000-0000-000000000000",
            HttpMethod.GET, withToken("test-internal-secret"), Void.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
