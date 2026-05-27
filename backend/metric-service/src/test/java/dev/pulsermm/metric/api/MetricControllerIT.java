package dev.pulsermm.metric.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class MetricControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("timescale/timescaledb:latest-pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("pulse")
        .withUsername("pulse")
        .withPassword("pulse");

    private static final String JWT_SECRET = "test-jwt-secret-32-chars-long-ok!";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("pulse.jwt.secret", () -> JWT_SECRET);
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registry.add("spring.rabbitmq.listener.direct.auto-startup", () -> "false");
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void testHeartbeat() throws Exception {
        var request = new ObjectMapper().createObjectNode();
        request.put("endpointId", UUID.randomUUID().toString());

        mvc.perform(post("/internal/heartbeat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
            .andExpect(status().isOk());
    }

    @Test
    void testHeartbeatTwice() throws Exception {
        UUID endpointId = UUID.randomUUID();
        var request = new ObjectMapper().createObjectNode();
        request.put("endpointId", endpointId.toString());

        mvc.perform(post("/internal/heartbeat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
            .andExpect(status().isOk());

        mvc.perform(post("/internal/heartbeat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
            .andExpect(status().isOk());
    }

    @Test
    void testIngestMetrics() throws Exception {
        var request = new ObjectMapper().createObjectNode();
        request.put("endpointId", UUID.randomUUID().toString());
        var samples = new ObjectMapper().createArrayNode();
        var sample = new ObjectMapper().createObjectNode();
        sample.put("type", "cpu");
        sample.put("value", 45.5);
        sample.put("collectedAt", System.currentTimeMillis());
        samples.add(sample);
        request.set("samples", samples);

        mvc.perform(post("/internal/metrics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
            .andExpect(status().isOk());
    }

    @Test
    void testIngestEmptyList() throws Exception {
        var request = new ObjectMapper().createObjectNode();
        request.put("endpointId", UUID.randomUUID().toString());
        request.set("samples", new ObjectMapper().createArrayNode());

        mvc.perform(post("/internal/metrics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
            .andExpect(status().isOk());
    }

    @Test
    void testQueryByLabelFiltersCorrectly() throws Exception {
        var mapper = new ObjectMapper();
        UUID endpointId = UUID.randomUUID();

        for (String core : new String[]{"0", "1"}) {
            var req = mapper.createObjectNode();
            req.put("endpointId", endpointId.toString());
            var samples = mapper.createArrayNode();
            var sample = mapper.createObjectNode();
            sample.put("type", "cpu.core");
            sample.put("value", Double.parseDouble("10." + core));
            sample.put("collectedAt", System.currentTimeMillis());
            var labels = mapper.createObjectNode();
            labels.put("core", core);
            sample.set("labels", labels);
            samples.add(sample);
            req.set("samples", samples);

            mvc.perform(post("/internal/metrics")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(req.toString()))
                .andExpect(status().isOk());
        }

        mvc.perform(get("/api/endpoints/" + endpointId + "/metrics" +
                "?from=2024-01-01T00:00:00Z&to=2099-01-01T00:00:00Z&type=cpu.core&label.core=0")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].value").value(10.0))
            .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void testSystemInfoReturns404WhenMissing() throws Exception {
        UUID endpointId = UUID.randomUUID();
        mvc.perform(get("/api/endpoints/" + endpointId + "/system-info")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void testSystemInfoUpsertAndGet() throws Exception {
        var mapper = new ObjectMapper();
        UUID endpointId = UUID.randomUUID();

        var req = mapper.createObjectNode();
        req.put("endpointId", endpointId.toString());
        req.put("cpuModel", "Test CPU v1");
        req.put("cpuPhysical", 4);
        req.put("cpuLogical", 8);
        req.put("cpuFreqMhz", 3200.0);
        req.put("ramTotal", 16_000_000_000L);
        req.put("swapTotal", 2_000_000_000L);
        req.put("collectedAt", System.currentTimeMillis());

        var disks = mapper.createArrayNode();
        var disk = mapper.createObjectNode();
        disk.put("device", "/dev/sda1");
        disk.put("mountpoint", "/");
        disk.put("fstype", "ext4");
        disk.put("totalBytes", 500_000_000_000L);
        disks.add(disk);
        req.set("disks", disks);

        var nics = mapper.createArrayNode();
        var nic = mapper.createObjectNode();
        nic.put("name", "eth0");
        nic.put("mac", "aa:bb:cc:dd:ee:ff");
        nic.put("mtu", 1500);
        var addrs = mapper.createArrayNode();
        addrs.add("192.168.1.10/24");
        nic.set("addresses", addrs);
        nics.add(nic);
        req.set("nics", nics);

        mvc.perform(post("/internal/system-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content(req.toString()))
            .andExpect(status().isNoContent());

        // second post with a new CPU model should overwrite
        req.put("cpuModel", "Test CPU v2");
        mvc.perform(post("/internal/system-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content(req.toString()))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/endpoints/" + endpointId + "/system-info")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cpuModel").value("Test CPU v2"))
            .andExpect(jsonPath("$.cpuLogical").value(8))
            .andExpect(jsonPath("$.disks[0].device").value("/dev/sda1"))
            .andExpect(jsonPath("$.nics[0].name").value("eth0"));
    }

    @Test
    void testSystemInfoRequiresAuth() throws Exception {
        mvc.perform(get("/api/endpoints/" + UUID.randomUUID() + "/system-info"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testQueryNoData() throws Exception {
        UUID endpointId = UUID.randomUUID();
        mvc.perform(get("/api/endpoints/" + endpointId + "/metrics?from=2024-01-01T00:00:00Z&to=2024-01-02T00:00:00Z&type=cpu")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0]").doesNotExist());
    }

    @Test
    void testQueryRequiresAuth() throws Exception {
        UUID endpointId = UUID.randomUUID();
        mvc.perform(get("/api/endpoints/" + endpointId + "/metrics?from=2024-01-01T00:00:00Z&to=2024-01-02T00:00:00Z&type=cpu"))
            .andExpect(status().isUnauthorized());
    }

    private String mintJwt(UUID userId) {
        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3_600_000))
            .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }
}
