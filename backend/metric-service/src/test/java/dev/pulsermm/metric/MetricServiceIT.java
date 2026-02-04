package dev.pulsermm.metric;

import dev.pulsermm.metric.domain.EndpointHeartbeat;
import dev.pulsermm.metric.infrastructure.EndpointHeartbeatRepository;
import dev.pulsermm.proto.v1.AgentServiceGrpc;
import dev.pulsermm.proto.v1.HeartbeatRequest;
import dev.pulsermm.proto.v1.MetricBatch;
import dev.pulsermm.proto.v1.MetricSample;
import dev.pulsermm.proto.v1.MetricServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Disabled;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@Disabled("Requires Docker/Podman - tested via e2e")
class MetricServiceIT {

    static final DockerImageName TIMESCALE_IMAGE =
        DockerImageName.parse("timescale/timescaledb:latest-pg16").asCompatibleSubstituteFor("postgres");

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(TIMESCALE_IMAGE)
        .withDatabaseName("pulse")
        .withUsername("postgres")
        .withPassword("pulse")
        // tmpfs bypasses podman anonymous-volume creation (crun can't create _data dir)
        .withTmpFs(java.util.Map.of("/var/lib/postgresql/data", "rw,size=512m"));

    static ManagedChannel channel;
    static AgentServiceGrpc.AgentServiceBlockingStub agentStub;
    static MetricServiceGrpc.MetricServiceBlockingStub metricStub;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeAll
    static void startContainer() {
        postgres.start();
        channel = ManagedChannelBuilder.forAddress("localhost", 9092)
            .usePlaintext()
            .build();
        agentStub = AgentServiceGrpc.newBlockingStub(channel);
        metricStub = MetricServiceGrpc.newBlockingStub(channel);
    }

    @AfterAll
    static void stopContainer() {
        channel.shutdown();
        postgres.stop();
    }

    @Autowired
    private EndpointHeartbeatRepository heartbeatRepo;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        heartbeatRepo.deleteAll();
        jdbc.update("DELETE FROM metric_samples");
    }

    @Test
    void heartbeatCreatesEntry() {
        UUID endpointId = UUID.randomUUID();
        agentStub.heartbeat(HeartbeatRequest.newBuilder()
            .setEndpointId(endpointId.toString())
            .build());

        EndpointHeartbeat hb = heartbeatRepo.findById(endpointId).orElseThrow();
        assertThat(hb.getStatus()).isEqualTo("online");
        assertThat(hb.getLastSeen()).isNotNull();
    }

    @Test
    void heartbeatIsIdempotent() {
        UUID endpointId = UUID.randomUUID();
        agentStub.heartbeat(HeartbeatRequest.newBuilder().setEndpointId(endpointId.toString()).build());
        agentStub.heartbeat(HeartbeatRequest.newBuilder().setEndpointId(endpointId.toString()).build());

        assertThat(heartbeatRepo.count()).isEqualTo(1);
    }

    @Test
    void pushMetricsStoresSamples() {
        UUID endpointId = UUID.randomUUID();
        long now = Instant.now().toEpochMilli();

        metricStub.pushMetrics(MetricBatch.newBuilder()
            .setEndpointId(endpointId.toString())
            .addSamples(MetricSample.newBuilder().setType("cpu").setValue(45.2).setCollectedAt(now).build())
            .addSamples(MetricSample.newBuilder().setType("ram").setValue(72.1).setCollectedAt(now).build())
            .build());

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT type, value FROM metric_samples WHERE endpoint_id = ?", endpointId);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(r -> r.get("type"))
            .containsExactlyInAnyOrder("cpu", "ram");
    }

    @Test
    void pushMetricsOrdersResultsAscending() {
        UUID endpointId = UUID.randomUUID();
        Instant t1 = Instant.now().minusSeconds(60);
        Instant t2 = Instant.now().minusSeconds(30);
        Instant t3 = Instant.now();

        metricStub.pushMetrics(MetricBatch.newBuilder()
            .setEndpointId(endpointId.toString())
            .addSamples(MetricSample.newBuilder().setType("cpu").setValue(10.0).setCollectedAt(t3.toEpochMilli()).build())
            .addSamples(MetricSample.newBuilder().setType("cpu").setValue(20.0).setCollectedAt(t1.toEpochMilli()).build())
            .addSamples(MetricSample.newBuilder().setType("cpu").setValue(15.0).setCollectedAt(t2.toEpochMilli()).build())
            .build());

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT value FROM metric_samples WHERE endpoint_id = ? AND type = 'cpu' ORDER BY sampled_at ASC",
            endpointId);

        assertThat(rows).hasSize(3);
        assertThat(((Number) rows.get(0).get("value")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) rows.get(2).get("value")).doubleValue()).isEqualTo(10.0);
    }

    @Test
    void load200ConcurrentAgentsPushMetrics() throws InterruptedException {
        int agentCount = 200;
        long now = Instant.now().toEpochMilli();

        ExecutorService pool = Executors.newFixedThreadPool(agentCount);
        CountDownLatch latch = new CountDownLatch(agentCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < agentCount; i++) {
            UUID endpointId = UUID.randomUUID();
            pool.submit(() -> {
                try {
                    metricStub.pushMetrics(MetricBatch.newBuilder()
                        .setEndpointId(endpointId.toString())
                        .addSamples(MetricSample.newBuilder()
                            .setType("cpu").setValue(50.0).setCollectedAt(now).build())
                        .addSamples(MetricSample.newBuilder()
                            .setType("ram").setValue(60.0).setCollectedAt(now).build())
                        .build());
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(errors.get()).isZero();

        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM metric_samples", Long.class);
        assertThat(count).isEqualTo(agentCount * 2L); // cpu + ram per agent
    }
}
