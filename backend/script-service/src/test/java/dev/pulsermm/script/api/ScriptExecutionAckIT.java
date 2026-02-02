package dev.pulsermm.script.api;

import dev.pulsermm.script.domain.Script;
import dev.pulsermm.script.infrastructure.persistence.ScriptRepository;
import dev.pulsermm.script.infrastructure.persistence.ScriptRunResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16:///testdb",
        "spring.datasource.username=testuser",
        "spring.datasource.password=testpass",
        "pulse.script.secret-kek=test-kek-that-is-at-least-16-bytes-long"
})
class ScriptExecutionAckIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScriptRepository scriptRepository;

    @Autowired
    private ScriptRunResultRepository scriptRunResultRepository;

    private UUID scriptId;
    private UUID userId;
    private UUID runId;
    private UUID endpoint1;

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID();
        endpoint1 = UUID.randomUUID();

        var script = new Script("Ack Test Script", "echo test", userId);
        script.setApprovedAt(OffsetDateTime.now());
        scriptId = scriptRepository.save(script).getId();

        var requestBody = """
                {
                  "endpointIds": ["%s"],
                  "secrets": {}
                }
                """.formatted(endpoint1);

        var response = mockMvc.perform(post("/api/scripts/{id}/run", scriptId)
                .with(request -> {
                    request.setUserPrincipal(() -> userId.toString());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isAccepted())
                .andReturn();

        runId = extractRunIdFromResponse(response.getResponse().getContentAsString());
    }

    @Test
    void agentAck_UpdatesResultWithExitCodeAndOutput() throws Exception {
        var ackRequest = """
                {
                  "exitCode": 0,
                  "output": "Script executed successfully"
                }
                """;

        mockMvc.perform(post("/api/scripts/runs/{runId}/endpoints/{endpointId}/ack", runId, endpoint1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ackRequest))
                .andExpect(status().isNoContent());

        var results = scriptRunResultRepository.findByRunId(runId);
        assertThat(results).hasSize(1);

        var result = results.get(0);
        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getOutput()).isEqualTo("Script executed successfully");
        assertThat(result.getAckedAt()).isNotNull();
        assertThat(result.isComplete()).isTrue();
    }

    @Test
    void agentAck_WithNonZeroExitCode() throws Exception {
        var ackRequest = """
                {
                  "exitCode": 1,
                  "output": "Error: command not found"
                }
                """;

        mockMvc.perform(post("/api/scripts/runs/{runId}/endpoints/{endpointId}/ack", runId, endpoint1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ackRequest))
                .andExpect(status().isNoContent());

        var results = scriptRunResultRepository.findByRunId(runId);
        var result = results.get(0);

        assertThat(result.getExitCode()).isEqualTo(1);
        assertThat(result.getOutput()).isEqualTo("Error: command not found");
    }

    @Test
    void agentAck_WithLargeOutput() throws Exception {
        var largeOutput = "x".repeat(10000);
        var ackRequest = """
                {
                  "exitCode": 0,
                  "output": "%s"
                }
                """.formatted(largeOutput);

        mockMvc.perform(post("/api/scripts/runs/{runId}/endpoints/{endpointId}/ack", runId, endpoint1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ackRequest))
                .andExpect(status().isNoContent());

        var results = scriptRunResultRepository.findByRunId(runId);
        var result = results.get(0);

        assertThat(result.getOutput()).hasSize(10000);
    }

    @Test
    void agentAck_ForNonExistentResult_Returns404() throws Exception {
        var nonExistentEndpoint = UUID.randomUUID();
        var ackRequest = """
                {
                  "exitCode": 0,
                  "output": "test"
                }
                """;

        mockMvc.perform(post("/api/scripts/runs/{runId}/endpoints/{endpointId}/ack", runId, nonExistentEndpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ackRequest))
                .andExpect(status().isNotFound());
    }

    @Test
    void agentAck_ValidatesExitCode() throws Exception {
        var invalidRequest = """
                {
                  "exitCode": null,
                  "output": "test"
                }
                """;

        mockMvc.perform(post("/api/scripts/runs/{runId}/endpoints/{endpointId}/ack", runId, endpoint1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    private UUID extractRunIdFromResponse(String json) {
        var startIdx = json.indexOf("\"runId\":\"") + 9;
        var endIdx = json.indexOf("\"", startIdx);
        return UUID.fromString(json.substring(startIdx, endIdx));
    }
}
