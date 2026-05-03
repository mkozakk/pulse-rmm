package dev.pulsermm.identity.api;

import dev.pulsermm.identity.infrastructure.EndpointGroupMembershipRepository;
import dev.pulsermm.identity.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Disabled("Requires Docker/Podman - tested via e2e")
class InternalRbacControllerIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository userRepository;
    @Autowired EndpointGroupMembershipRepository membershipRepository;

    @BeforeEach
    void cleanDb() {
        membershipRepository.deleteAll();
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

    @Test
    void setAndGetEndpointGroup() {
        UUID endpointId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        HttpHeaders h = new HttpHeaders();
        h.set("X-Internal-Token", "test-internal-secret");
        h.setContentType(MediaType.APPLICATION_JSON);
        var put = rest.exchange(
            "/internal/rbac/endpoint-groups/" + endpointId,
            HttpMethod.PUT, new HttpEntity<>(groupId, h), Void.class);
        assertThat(put.getStatusCode().value()).isEqualTo(200);

        var get = rest.exchange(
            "/internal/rbac/endpoint-groups/" + endpointId,
            HttpMethod.GET, withToken("test-internal-secret"), UUID.class);
        assertThat(get.getStatusCode().value()).isEqualTo(200);
        assertThat(get.getBody()).isEqualTo(groupId);
    }

    @Test
    void getEndpointGroupUnknownReturns404() {
        var response = rest.exchange(
            "/internal/rbac/endpoint-groups/" + UUID.randomUUID(),
            HttpMethod.GET, withToken("test-internal-secret"), Void.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
