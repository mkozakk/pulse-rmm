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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@Disabled("Requires Docker/Podman - tested via e2e")
class TagRuleIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("pulse")
        .withUsername("pulse")
        .withPassword("pulse");

    @MockBean
    IdentityServiceClient identityClient;

    @LocalServerPort
    int port;

    @Autowired TestRestTemplate restTemplate;
    @Autowired GroupRepository groupRepository;
    @Autowired EndpointRepository endpointRepository;
    @Autowired EndpointTagRepository endpointTagRepository;
    @Autowired TagRuleRepository tagRuleRepository;
    @Autowired EnrolmentTokenRepository enrolmentTokenRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Environment env;

    private String token;
    private UUID endpointId;

    @BeforeEach
    void setUp() {
        endpointTagRepository.deleteAll();
        endpointRepository.deleteAll();
        enrolmentTokenRepository.deleteAll();
        jdbcTemplate.update("UPDATE enrolment.groups SET parent_id = NULL");
        groupRepository.deleteAll();
        tagRuleRepository.deleteAll();

        String jwtSecret = env.getProperty("pulse.jwt.secret");
        token = new TestJwtHelper(jwtSecret).createToken();

        Group group = new Group(UUID.randomUUID(), "default", null);
        groupRepository.save(group);

        Endpoint e = new Endpoint(
            UUID.randomUUID(), "host1", "linux", "x86_64",
            group.getId(), new byte[32], Instant.now(), Instant.now()
        );
        endpointRepository.save(e);
        endpointId = e.getId();
    }

    @Test
    void createAndListRules() {
        TagRuleResponse created = post("/api/tag-rules",
            new CreateTagRuleRequest("os", "linux", "os", "linux"),
            TagRuleResponse.class).getBody();

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.conditionField()).isEqualTo("os");

        TagRuleResponse[] list = get("/api/tag-rules", TagRuleResponse[].class);
        assertThat(list).hasSize(1);
        assertThat(list[0].id()).isEqualTo(created.id());
    }

    @Test
    void deleteRule() {
        TagRuleResponse rule = post("/api/tag-rules",
            new CreateTagRuleRequest("os", "linux", "os", "linux"),
            TagRuleResponse.class).getBody();

        ResponseEntity<Void> del = restTemplate.exchange(
            "/api/tag-rules/" + rule.id(), HttpMethod.DELETE, authHeader(), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        TagRuleResponse[] list = get("/api/tag-rules", TagRuleResponse[].class);
        assertThat(list).isEmpty();
    }

    @Test
    void evaluateAppliesTagsToMatchingEndpoints() {
        post("/api/tag-rules",
            new CreateTagRuleRequest("os", "linux", "os", "linux"),
            TagRuleResponse.class);

        ResponseEntity<Void> eval = post("/api/tag-rules/evaluate", null, Void.class);
        assertThat(eval.getStatusCode()).isEqualTo(HttpStatus.OK);

        EndpointResponse[] result = getWithTag("os=linux");
        assertThat(result).hasSize(1);
        assertThat(result[0].id()).isEqualTo(endpointId);
    }

    @Test
    void evaluateDoesNotTagNonMatchingEndpoints() {
        post("/api/tag-rules",
            new CreateTagRuleRequest("os", "windows", "os", "windows"),
            TagRuleResponse.class);

        post("/api/tag-rules/evaluate", null, Void.class);

        EndpointResponse[] result = getWithTag("os=windows");
        assertThat(result).isEmpty();
    }

    private <T> ResponseEntity<T> post(String path, Object body, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
    }

    private <T> T get(String path, Class<T> responseType) {
        return restTemplate.exchange(path, HttpMethod.GET, authHeader(), responseType).getBody();
    }

    private EndpointResponse[] getWithTag(String tagValue) {
        URI uri = UriComponentsBuilder
            .fromHttpUrl("http://localhost:" + port + "/api/endpoints")
            .queryParam("tag", tagValue)
            .build().toUri();
        return restTemplate.getRestTemplate()
            .exchange(uri, HttpMethod.GET, authHeader(), EndpointResponse[].class)
            .getBody();
    }

    private HttpEntity<Void> authHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return new HttpEntity<>(headers);
    }
}
