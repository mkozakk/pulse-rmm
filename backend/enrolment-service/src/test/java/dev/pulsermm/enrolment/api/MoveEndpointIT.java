package dev.pulsermm.enrolment.api;

import dev.pulsermm.enrolment.TestJwtHelper;
import dev.pulsermm.enrolment.domain.Endpoint;
import dev.pulsermm.enrolment.domain.Group;
import dev.pulsermm.enrolment.infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@Disabled("Requires Docker/Podman - tested via e2e")
class MoveEndpointIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("pulse")
        .withUsername("pulse")
        .withPassword("pulse");

    @MockBean
    IdentityServiceClient identityClient;

    @Autowired TestRestTemplate restTemplate;
    @Autowired GroupRepository groupRepository;
    @Autowired EndpointRepository endpointRepository;
    @Autowired EndpointTagRepository endpointTagRepository;
    @Autowired EnrolmentTokenRepository enrolmentTokenRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Environment env;

    private String token;
    private UUID groupA;
    private UUID groupB;
    private UUID endpointId;

    @BeforeEach
    void setUp() {
        endpointTagRepository.deleteAll();
        endpointRepository.deleteAll();
        enrolmentTokenRepository.deleteAll();
        jdbcTemplate.update("UPDATE enrolment.groups SET parent_id = NULL");
        groupRepository.deleteAll();

        String jwtSecret = env.getProperty("pulse.jwt.secret");
        token = new TestJwtHelper(jwtSecret).createToken();

        Group a = new Group(UUID.randomUUID(), "GroupA", null);
        Group b = new Group(UUID.randomUUID(), "GroupB", null);
        groupRepository.save(a);
        groupRepository.save(b);
        groupA = a.getId();
        groupB = b.getId();

        Endpoint e = new Endpoint(
            UUID.randomUUID(), "host1", "linux", "x86_64",
            groupA, new byte[32], Instant.now(), Instant.now()
        );
        endpointRepository.save(e);
        endpointId = e.getId();
    }

    @Test
    void moveEndpointToNewGroup() {
        ResponseEntity<Void> response = put("/api/endpoints/" + endpointId + "/group",
            new MoveEndpointRequest(groupB));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        EndpointResponse[] list = restTemplate.exchange(
            "/api/endpoints", HttpMethod.GET, authHeader(), EndpointResponse[].class).getBody();
        assertThat(list).hasSize(1);
        assertThat(list[0].groupId()).isEqualTo(groupB);
    }

    @Test
    void moveToNonExistentGroupReturns404() {
        ResponseEntity<Void> response = put("/api/endpoints/" + endpointId + "/group",
            new MoveEndpointRequest(UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void moveNonExistentEndpointReturns404() {
        ResponseEntity<Void> response = put("/api/endpoints/" + UUID.randomUUID() + "/group",
            new MoveEndpointRequest(groupB));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<Void> put(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers), Void.class);
    }

    private HttpEntity<Void> authHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return new HttpEntity<>(headers);
    }
}
