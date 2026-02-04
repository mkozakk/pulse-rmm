package dev.pulsermm.enrolment.api;

import dev.pulsermm.enrolment.TestJwtHelper;
import dev.pulsermm.enrolment.infrastructure.EnrolmentTokenRepository;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import dev.pulsermm.enrolment.infrastructure.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@org.springframework.test.context.ActiveProfiles("test")
@Disabled("Requires Docker/Podman - tested via e2e")
class GroupApiIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("pulse")
        .withUsername("pulse")
        .withPassword("pulse");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private EndpointRepository endpointRepository;

    @Autowired
    private EnrolmentTokenRepository enrolmentTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment env;

    private String token;

    @BeforeEach
    void setUp() {
        endpointRepository.deleteAll();
        enrolmentTokenRepository.deleteAll();
        // groups has a self-referential FK; null parent_id first so deleteAll works
        jdbcTemplate.update("UPDATE enrolment.groups SET parent_id = NULL");
        groupRepository.deleteAll();

        String jwtSecret = env.getProperty("pulse.jwt.secret");
        token = new TestJwtHelper(jwtSecret).createToken();
    }

    @Test
    void createRootGroup() {
        var request = new CreateGroupRequest("HQ", null);
        ResponseEntity<GroupResponse> response = post("/api/groups", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("HQ");
        assertThat(response.getBody().parentId()).isNull();
    }

    @Test
    void createChildGroup() {
        UUID parentId = post("/api/groups", new CreateGroupRequest("HQ", null)).getBody().id();

        ResponseEntity<GroupResponse> response = post("/api/groups", new CreateGroupRequest("Sales", parentId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().parentId()).isEqualTo(parentId);
    }

    @Test
    void nonExistentParentReturnsBadRequest() {
        var request = new CreateGroupRequest("Orphan", UUID.randomUUID());
        ResponseEntity<Void> response = post("/api/groups", request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void depthLimitRejected() {
        UUID current = post("/api/groups", new CreateGroupRequest("L1", null)).getBody().id();
        for (int i = 2; i <= 5; i++) {
            current = post("/api/groups", new CreateGroupRequest("L" + i, current)).getBody().id();
        }

        ResponseEntity<Void> response = post("/api/groups", new CreateGroupRequest("L6", current), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private <T> ResponseEntity<T> post(String path, Object body, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(path, new HttpEntity<>(body, headers), responseType);
    }

    private ResponseEntity<GroupResponse> post(String path, Object body) {
        return post(path, body, GroupResponse.class);
    }
}
