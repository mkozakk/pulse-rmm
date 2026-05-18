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
    void testQueryNoData() throws Exception {
        UUID endpointId = UUID.randomUUID();
        mvc.perform(get("/api/endpoints/" + endpointId + "/metrics?from=0&to=999999999&type=cpu")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0]").doesNotExist());
    }

    @Test
    void testQueryRequiresAuth() throws Exception {
        UUID endpointId = UUID.randomUUID();
        mvc.perform(get("/api/endpoints/" + endpointId + "/metrics?from=0&to=999999999"))
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
