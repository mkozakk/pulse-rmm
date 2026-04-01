package dev.pulsermm.commands.processes;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.commands.processes.infrastructure.ProcessSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ProcessControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
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
        registry.add("pulse.script.secret-kek", () -> "test-kek-32-chars-long-ok-here!!");
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registry.add("spring.rabbitmq.listener.direct.auto-startup", () -> "false");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProcessSnapshotRepository snapshotRepository;

    @BeforeEach
    void clear() {
        snapshotRepository.deleteAll();
    }

    @Test
    void refreshReturns201AndInsertsPendingSnapshot() throws Exception {
        UUID endpointId = UUID.randomUUID();
        mvc.perform(post("/api/endpoints/" + endpointId + "/processes/refresh")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.commandId").exists());

        var rows = snapshotRepository.findAll();
        assert rows.size() == 1;
        assert rows.get(0).getStatus().equals("PENDING");
        assert rows.get(0).getEndpointId().equals(endpointId);
    }

    @Test
    void refreshRequiresAuth() throws Exception {
        mvc.perform(post("/api/endpoints/" + UUID.randomUUID() + "/processes/refresh"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void latestReturnsCompletedSnapshotAfterAck() throws Exception {
        UUID endpointId = UUID.randomUUID();
        var resp = mvc.perform(post("/api/endpoints/" + endpointId + "/processes/refresh")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
            .andExpect(status().isCreated())
            .andReturn();
        var commandId = new ObjectMapper().readTree(resp.getResponse().getContentAsString())
            .get("commandId").asText();

        var ack = new ObjectMapper().createObjectNode();
        ack.put("exitCode", 0);
        ack.put("output", "[{\"pid\":42,\"name\":\"init\",\"username\":\"root\",\"cpuPercent\":0.1,\"memoryBytes\":1024}]");

        mvc.perform(post("/api/processes/commands/" + commandId + "/ack")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ack.toString()))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/endpoints/" + endpointId + "/processes/latest")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.processes[0].pid").value(42))
            .andExpect(jsonPath("$.processes[0].name").value("init"));
    }

    @Test
    void latestReturns404WhenNoSnapshot() throws Exception {
        mvc.perform(get("/api/endpoints/" + UUID.randomUUID() + "/processes/latest")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void killReturns202() throws Exception {
        UUID endpointId = UUID.randomUUID();
        mvc.perform(post("/api/endpoints/" + endpointId + "/processes/1234/kill")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.commandId").exists());
    }
}
