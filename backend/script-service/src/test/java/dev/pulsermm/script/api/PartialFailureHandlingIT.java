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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
class PartialFailureHandlingIT {

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
        var script = new Script("Partial Failure Test", "echo test", userId);
        script.setApprovedAt(OffsetDateTime.now());
        scriptId = scriptRepository.save(script).getId();
    }

    @Test
    void partialFailure_SomeEndpointsAck_OthersRemainPending() throws Exception {
        var endpoints = IntStream.range(0, 10)
                .mapToObj(i -> UUID.randomUUID().toString())
                .collect(Collectors.toList());

        var endpointJson = endpoints.stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(","));

        var runRequest = """
                {
                  "endpointIds": [%s],
                  "secrets": {}
                }
                """.formatted(endpointJson);

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

        // Only ack 3 out of 10 endpoints
        for (int i = 0; i < 3; i++) {
            var ackRequest = """
                    {
                      "exitCode": 0,
                      "output": "success"
                    }
                    """;

            mockMvc.perform(post("/api/scripts/runs/{runId}/endpoints/{endpointId}/ack", runId, UUID.fromString(endpoints.get(i)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(ackRequest))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(get("/api/scripts/runs/{runId}/results", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.pending").value(7));

        var results = scriptRunResultRepository.findByRunId(runId);
        var completed = results.stream().filter(r -> !r.isPending()).count();
        var pending = results.stream().filter(r -> r.isPending()).count();

        assertThat(completed).isEqualTo(3);
        assertThat(pending).isEqualTo(7);
    }

    @Test
    void partialFailure_MixedExitCodes() throws Exception {
        var endpoint1 = UUID.randomUUID();
        var endpoint2 = UUID.randomUUID();
        var endpoint3 = UUID.randomUUID();

        var runRequest = """
                {
                  "endpointIds": ["%s", "%s", "%s"],
                  "secrets": {}
                }
                """.formatted(endpoint1, endpoint2, endpoint3);

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

        // Endpoint 1: success (0)
        mockMvc.perform(post("/api/scripts/runs/{runId}/endpoints/{endpointId}/ack", runId, endpoint1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"exitCode\": 0, \"output\": \"success\"}"))
                .andExpect(status().isNoContent());

        // Endpoint 2: failure (1)
        mockMvc.perform(post("/api/scripts/runs/{runId}/endpoints/{endpointId}/ack", runId, endpoint2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"exitCode\": 1, \"output\": \"error\"}"))
                .andExpect(status().isNoContent());

        // Endpoint 3: not acked (stays pending)

        mockMvc.perform(get("/api/scripts/runs/{runId}/results", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.pending == false)].length()").value(2))
                .andExpect(jsonPath("$.results[?(@.pending == true)].length()").value(1));
    }

    private UUID extractRunIdFromResponse(String json) {
        var startIdx = json.indexOf("\"runId\":\"") + 9;
        var endIdx = json.indexOf("\"", startIdx);
        return UUID.fromString(json.substring(startIdx, endIdx));
    }
}
