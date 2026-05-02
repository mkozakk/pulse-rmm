package dev.pulsermm.script.api;

import dev.pulsermm.script.domain.Script;
import dev.pulsermm.script.domain.ScriptRunResult;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class End2EndScriptExecutionIT {

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

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        var script = new Script("E2E Test Script", "echo 'hello world'", userId);
        script.setApprovedAt(OffsetDateTime.now());
        scriptId = scriptRepository.save(script).getId();
    }

    @Test
    void end2End_Initiate_Poll_And_Ack_Flow() throws Exception {
        var endpoint1 = UUID.randomUUID();
        var endpoint2 = UUID.randomUUID();

        var runRequest = """
                {
                  "endpointIds": ["%s", "%s"],
                  "secrets": {}
                }
                """.formatted(endpoint1, endpoint2);

        var response = mockMvc.perform(post("/api/scripts/{id}/run", scriptId)
                .with(request -> {
                    request.setUserPrincipal(() -> userId.toString());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(runRequest))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").exists())
                .andReturn();

        var runId = extractRunIdFromResponse(response.getResponse().getContentAsString());

        mockMvc.perform(get("/api/scripts/runs/{runId}/results", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.pending").value(2));

        var ackRequest1 = """
                {
                  "exitCode": 0,
                  "output": "hello world"
                }
                """;

        mockMvc.perform(post("/api/scripts/runs/{runId}/endpoints/{endpointId}/ack", runId, endpoint1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ackRequest1))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/scripts/runs/{runId}/results", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(1));

        var ackRequest2 = """
                {
                  "exitCode": 0,
                  "output": "hello world"
                }
                """;

        mockMvc.perform(post("/api/scripts/runs/{runId}/endpoints/{endpointId}/ack", runId, endpoint2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ackRequest2))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/scripts/runs/{runId}/results", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(0))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void end2End_VerifyAllResultsPersistedCorrectly() throws Exception {
        var endpoints = new UUID[]{UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};

        var endpointIds = String.join("\",\"",
                endpoints[0].toString(),
                endpoints[1].toString(),
                endpoints[2].toString());

        var runRequest = """
                {
                  "endpointIds": ["%s"],
                  "secrets": {}
                }
                """.formatted(endpointIds);

        var response = mockMvc.perform(post("/api/scripts/{id}/run", scriptId)
                .with(request -> {
                    request.setUserPrincipal(() -> userId.toString());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(runRequest))
                .andExpect(status().isAccepted())
                .andReturn();

        var runId = extractRunIdFromResponse(response.getResponse().getContentAsString());

        for (int i = 0; i < endpoints.length; i++) {
            var ackRequest = """
                    {
                      "exitCode": %d,
                      "output": "output-%d"
                    }
                    """.formatted(i, i);

            mockMvc.perform(post("/api/scripts/runs/{runId}/endpoints/{endpointId}/ack", runId, endpoints[i])
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(ackRequest))
                    .andExpect(status().isNoContent());
        }

        var allResults = scriptRunResultRepository.findByRunId(runId);
        assertThat(allResults).hasSize(3);
        assertThat(allResults).allMatch(ScriptRunResult::isComplete);

        for (int i = 0; i < endpoints.length; i++) {
            var endpointId = endpoints[i];
            var result = allResults.stream()
                    .filter(r -> r.getEndpointId().equals(endpointId))
                    .findFirst()
                    .orElseThrow();

            assertThat(result.getExitCode()).isEqualTo(i);
            assertThat(result.getOutput()).isEqualTo("output-" + i);
            assertThat(result.getAckedAt()).isNotNull();
        }
    }

    private UUID extractRunIdFromResponse(String json) {
        var startIdx = json.indexOf("\"runId\":\"") + 9;
        var endIdx = json.indexOf("\"", startIdx);
        return UUID.fromString(json.substring(startIdx, endIdx));
    }
}
