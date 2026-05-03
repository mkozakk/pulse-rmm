package dev.pulsermm.enrolment.infrastructure;

import dev.pulsermm.enrolment.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@Disabled("Requires Docker/Podman - tested via e2e")
class TagSchemaMigrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("pulse")
        .withUsername("pulse")
        .withPassword("pulse");

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private EndpointRepository endpointRepository;

    @Autowired
    private EndpointTagRepository endpointTagRepository;

    @Autowired
    private EnrolmentTokenRepository enrolmentTokenRepository;

    @Autowired
    private TagRuleRepository tagRuleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID endpointId;

    @BeforeEach
    void setUp() {
        endpointTagRepository.deleteAll();
        endpointRepository.deleteAll();
        enrolmentTokenRepository.deleteAll();
        jdbcTemplate.update("UPDATE enrolment.groups SET parent_id = NULL");
        groupRepository.deleteAll();
        tagRuleRepository.deleteAll();

        Group group = new Group(UUID.randomUUID(), "test-group", null);
        groupRepository.save(group);

        Endpoint endpoint = new Endpoint(
            UUID.randomUUID(), "host1", "linux", "x86_64",
            group.getId(), new byte[32], Instant.now(), Instant.now()
        );
        endpointRepository.save(endpoint);
        endpointId = endpoint.getId();
    }

    @Test
    void testInsertAndQueryEndpointTag() {
        EndpointTagId tagId = new EndpointTagId(endpointId, "env");
        endpointTagRepository.save(new EndpointTag(tagId, "prod"));

        List<EndpointTag> tags = endpointTagRepository.findAllByIdEndpointId(endpointId);

        assertThat(tags).hasSize(1);
        assertThat(tags.get(0).getId().getKey()).isEqualTo("env");
        assertThat(tags.get(0).getValue()).isEqualTo("prod");
    }

    @Test
    void testInsertAndQueryTagRule() {
        TagRule rule = new TagRule("os", "linux", "os", "linux");
        TagRule saved = tagRuleRepository.save(rule);

        assertThat(saved.getId()).isNotNull();
        assertThat(tagRuleRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void testDeleteAllTagsByEndpoint() {
        endpointTagRepository.save(new EndpointTag(new EndpointTagId(endpointId, "env"), "prod"));
        endpointTagRepository.save(new EndpointTag(new EndpointTagId(endpointId, "site"), "warsaw"));

        endpointTagRepository.deleteAllByIdEndpointId(endpointId);

        assertThat(endpointTagRepository.findAllByIdEndpointId(endpointId)).isEmpty();
    }
}
