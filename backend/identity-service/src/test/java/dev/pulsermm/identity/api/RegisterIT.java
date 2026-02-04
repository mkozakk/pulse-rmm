package dev.pulsermm.identity.api;

import dev.pulsermm.identity.infrastructure.UserRepository;
import dev.pulsermm.identity.infrastructure.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Disabled("Java 21 context loading incompatibility")
@ActiveProfiles("test")
class RegisterIT {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserRoleRepository userRoleRepository;

    @BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
    }

    private HttpEntity<Object> json(Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }

    @Test
    void firstRegisterReturns201WithIdAndHashedPassword() {
        var response = rest.postForEntity("/api/auth/register",
            json(Map.of("username", "admin", "password", "validpassword12")), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(UUID.fromString((String) response.getBody().get("id"))).isNotNull();

        var user = userRepository.findByUsername("admin").orElseThrow();
        assertThat(user.getPasswordHash()).isNotBlank();
        assertThat(user.getPasswordHash()).isNotEqualTo("validpassword12");
    }

    @Test
    void secondRegisterReturns409RegistrationDisabled() {
        rest.postForEntity("/api/auth/register",
            json(Map.of("username", "admin", "password", "validpassword12")), Void.class);

        var second = rest.postForEntity("/api/auth/register",
            json(Map.of("username", "other", "password", "validpassword12")), Map.class);

        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(second.getBody().get("error")).isEqualTo("registration_disabled");
    }

    @Test
    void concurrentRegistrationOnlyOneSucceeds() throws InterruptedException {
        var latch = new CountDownLatch(1);
        var status1 = new AtomicInteger();
        var status2 = new AtomicInteger();

        var t1 = Thread.ofVirtual().start(() -> {
            try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            status1.set(rest.postForEntity("/api/auth/register",
                json(Map.of("username", "admin", "password", "validpassword12")), Void.class)
                .getStatusCode().value());
        });
        var t2 = Thread.ofVirtual().start(() -> {
            try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            status2.set(rest.postForEntity("/api/auth/register",
                json(Map.of("username", "admin", "password", "validpassword12")), Void.class)
                .getStatusCode().value());
        });

        latch.countDown();
        t1.join(5000);
        t2.join(5000);

        assertThat(status1.get() + status2.get()).isEqualTo(201 + 409);
    }

    static Stream<String> invalidUsernames() {
        return Stream.of("ab", "a".repeat(65), "user-name", "user.name", "user name", "user@name", "");
    }

    @ParameterizedTest
    @MethodSource("invalidUsernames")
    void invalidUsernameReturns400(String username) {
        var body = new HashMap<String, Object>();
        body.put("username", username);
        body.put("password", "validpassword12");
        var response = rest.postForEntity("/api/auth/register", json(body), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void nullUsernameReturns400() {
        var body = new HashMap<String, Object>();
        body.put("username", null);
        body.put("password", "validpassword12");
        var response = rest.postForEntity("/api/auth/register", json(body), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void minUsername3CharsReturns201() {
        var response = rest.postForEntity("/api/auth/register",
            json(Map.of("username", "abc", "password", "validpassword12")), Void.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void maxUsername64CharsReturns201() {
        var response = rest.postForEntity("/api/auth/register",
            json(Map.of("username", "a".repeat(64), "password", "validpassword12")), Void.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    static Stream<String> invalidPasswords() {
        return Stream.of("short11char", "a".repeat(73));
    }

    @ParameterizedTest
    @MethodSource("invalidPasswords")
    void invalidPasswordReturns400(String password) {
        var response = rest.postForEntity("/api/auth/register",
            json(Map.of("username", "admin", "password", password)), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void minPassword12CharsReturns201() {
        var response = rest.postForEntity("/api/auth/register",
            json(Map.of("username", "admin", "password", "a".repeat(12))), Void.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void maxPassword72CharsReturns201() {
        var response = rest.postForEntity("/api/auth/register",
            json(Map.of("username", "admin", "password", "a".repeat(72))), Void.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void malformedJsonReturns400() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        var response = rest.postForEntity("/api/auth/register",
            new HttpEntity<>("{not valid json", h), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void missingContentTypeReturns415() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.TEXT_PLAIN);
        var response = rest.postForEntity("/api/auth/register",
            new HttpEntity<>("{\"username\":\"admin\",\"password\":\"validpassword12\"}", h), Void.class);
        assertThat(response.getStatusCode().value()).isEqualTo(415);
    }

    @Test
    void validationErrorResponseShape() {
        var response = rest.postForEntity("/api/auth/register",
            json(Map.of("username", "ab", "password", "validpassword12")), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsKeys("error", "message");
        assertThat(response.getBody().get("error")).isEqualTo("validation_failed");
    }

    @Test
    void firstUserIsAssignedAdminRole() {
        rest.postForEntity("/api/auth/register",
            json(Map.of("username", "admin", "password", "validpassword12")), Map.class);
        var user = userRepository.findByUsername("admin").orElseThrow();
        assertThat(userRoleRepository.findAllByIdUserId(user.getId())).isNotEmpty();
    }

    @Test
    void conflictErrorResponseShape() {
        rest.postForEntity("/api/auth/register",
            json(Map.of("username", "admin", "password", "validpassword12")), Void.class);
        var response = rest.postForEntity("/api/auth/register",
            json(Map.of("username", "other", "password", "validpassword12")), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).containsKeys("error", "message");
        assertThat(response.getBody().get("error")).isEqualTo("registration_disabled");
    }
}
