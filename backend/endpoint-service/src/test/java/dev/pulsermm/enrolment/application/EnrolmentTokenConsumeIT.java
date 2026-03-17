package dev.pulsermm.enrolment.application;

import dev.pulsermm.endpoint.EndpointApplication;
import dev.pulsermm.enrolment.api.errors.InvalidTokenException;
import dev.pulsermm.enrolment.domain.EnrolmentToken;
import dev.pulsermm.enrolment.domain.Group;
import dev.pulsermm.enrolment.infrastructure.EnrolmentTokenRepository;
import dev.pulsermm.enrolment.infrastructure.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@Testcontainers
@SpringBootTest(classes = EndpointApplication.class)
@ActiveProfiles("test")
@Import(EnrolmentTokenConsumeIT.MockRabbitConfig.class)
class EnrolmentTokenConsumeIT {

    @TestConfiguration
    static class MockRabbitConfig {
        @Bean
        RabbitTemplate rabbitTemplate() {
            return mock(RabbitTemplate.class);
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("pulse")
        .withUsername("pulse")
        .withPassword("pulse");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EnrolService enrolService;

    @Autowired
    private EnrolmentTokenRepository tokenRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID groupId;

    @BeforeEach
    void cleanDb() {
        jdbc.update("DELETE FROM enrolment.endpoints");
        jdbc.update("DELETE FROM enrolment.enrolment_tokens");
        jdbc.update("DELETE FROM enrolment.groups");
        Group g = new Group(UUID.randomUUID(), "test-group", null);
        groupRepository.save(g);
        groupId = g.getId();
    }

    @Test
    void validTokenIsConsumedAfterFirstSuccessfulEnrol() {
        UUID tokenId = createToken(groupId, Instant.now().plus(1, ChronoUnit.HOURS), false);

        UUID endpointId = enrolService.enrol(tokenId, randomPubKey(), "host-a", "linux", "amd64");

        assertThat(endpointId).isNotNull();
        Instant consumedAt = jdbc.queryForObject(
            "SELECT consumed_at FROM enrolment.enrolment_tokens WHERE id = ?",
            Instant.class, tokenId);
        UUID consumedBy = jdbc.queryForObject(
            "SELECT consumed_by_endpoint FROM enrolment.enrolment_tokens WHERE id = ?",
            UUID.class, tokenId);
        assertThat(consumedAt).isNotNull();
        assertThat(consumedBy).isEqualTo(endpointId);
    }

    @Test
    void consumedTokenCannotBeReusedByDifferentAgent() {
        UUID tokenId = createToken(groupId, Instant.now().plus(1, ChronoUnit.HOURS), false);

        enrolService.enrol(tokenId, randomPubKey(), "host-a", "linux", "amd64");

        assertThatThrownBy(() ->
            enrolService.enrol(tokenId, randomPubKey(), "host-b", "linux", "amd64"))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        UUID tokenId = createToken(groupId, Instant.now().minus(1, ChronoUnit.HOURS), false);

        assertThatThrownBy(() ->
            enrolService.enrol(tokenId, randomPubKey(), "host-a", "linux", "amd64"))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void revokedTokenIsRejected() {
        UUID tokenId = createToken(groupId, Instant.now().plus(1, ChronoUnit.HOURS), true);

        assertThatThrownBy(() ->
            enrolService.enrol(tokenId, randomPubKey(), "host-a", "linux", "amd64"))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void concurrentEnrolWithSameTokenExactlyOneWins() throws Exception {
        UUID tokenId = createToken(groupId, Instant.now().plus(1, ChronoUnit.HOURS), false);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final byte[] pk = randomPubKey();
            final String host = "host-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    enrolService.enrol(tokenId, pk, host, "linux", "amd64");
                    successes.incrementAndGet();
                } catch (InvalidTokenException e) {
                    failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(threads - 1);
    }

    private UUID createToken(UUID groupId, Instant expiresAt, boolean revoked) {
        UUID id = UUID.randomUUID();
        EnrolmentToken token = new EnrolmentToken(id, groupId, expiresAt, revoked, Instant.now());
        tokenRepository.save(token);
        return id;
    }

    private byte[] randomPubKey() {
        byte[] key = new byte[32];
        new java.util.Random().nextBytes(key);
        return key;
    }
}
