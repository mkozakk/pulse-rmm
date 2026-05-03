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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@Disabled("Requires Docker/Podman - tested via e2e")
class EndpointTagIT {

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
    void setTagsReplacesAll() {
        put("/api/endpoints/" + endpointId + "/tags", new SetTagsRequest(List.of(new TagEntry("env", "prod"))));
        put("/api/endpoints/" + endpointId + "/tags", new SetTagsRequest(List.of(new TagEntry("env", "staging"))));

        endpointTagRepository.findAllByIdEndpointId(endpointId).forEach(t ->
            assertThat(t.getValue()).isEqualTo("staging")
        );
    }

    @Test
    void filterByTag() {
        put("/api/endpoints/" + endpointId + "/tags", new SetTagsRequest(List.of(new TagEntry("env", "prod"))));

        EndpointResponse[] result = getWithTags("env=prod");
        assertThat(result).hasSize(1);
        assertThat(result[0].id()).isEqualTo(endpointId);
    }

    @Test
    void filterByMultipleTagsAnd() {
        put("/api/endpoints/" + endpointId + "/tags",
            new SetTagsRequest(List.of(new TagEntry("env", "prod"), new TagEntry("site", "warsaw"))));

        EndpointResponse[] both = getWithTags("env=prod", "site=warsaw");
        assertThat(both).hasSize(1);

        EndpointResponse[] onlyOne = getWithTags("env=prod", "site=paris");
        assertThat(onlyOne).isEmpty();
    }

    @Test
    void filterByTagNoMatch() {
        EndpointResponse[] result = getWithTags("env=dev");
        assertThat(result).isEmpty();
    }

    private void put(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers), Void.class);
    }

    private EndpointResponse[] getWithTags(String... tagValues) {
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromHttpUrl("http://localhost:" + port + "/api/endpoints");
        for (String tag : tagValues) {
            builder.queryParam("tag", tag);
        }
        URI uri = builder.build().toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return restTemplate.getRestTemplate()
            .exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), EndpointResponse[].class)
            .getBody();
    }
}
