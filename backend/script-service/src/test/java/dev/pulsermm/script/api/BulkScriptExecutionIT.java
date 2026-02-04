package dev.pulsermm.script.api;

import dev.pulsermm.script.domain.Script;
import dev.pulsermm.script.infrastructure.persistence.ScriptRepository;
import dev.pulsermm.script.infrastructure.persistence.ScriptRunResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Disabled;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16:///testdb",
        "spring.datasource.username=testuser",
        "spring.datasource.password=testpass"
})
@Disabled("Requires Docker/Podman - tested via e2e")
class BulkScriptExecutionIT {

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
        var script = new Script("Bulk Test Script", "echo test", userId);
        script.setApprovedAt(OffsetDateTime.now());
        scriptId = scriptRepository.save(script).getId();
    }

    @Test
    void bulkExecution_DispatchesTo100Endpoints_Within500ms() throws Exception {
        var endpoints = IntStream.range(0, 100)
                .mapToObj(i -> UUID.randomUUID().toString())
                .collect(Collectors.toList());

        var endpointJson = endpoints.stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(","));

        var requestBody = """
                {
                  "endpointIds": [%s],
                  "secrets": {}
                }
                """.formatted(endpointJson);

        var startTime = System.currentTimeMillis();

        var response = mockMvc.perform(post("/api/scripts/{id}/run", scriptId)
                .with(request -> {
                    request.setUserPrincipal(() -> userId.toString());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isAccepted())
                .andReturn();

        var elapsedMs = System.currentTimeMillis() - startTime;

        var runId = extractRunIdFromResponse(response.getResponse().getContentAsString());
        var results = scriptRunResultRepository.findByRunId(runId);

        assertThat(results).hasSize(100);
        assertThat(elapsedMs).isLessThan(500);
    }

    private UUID extractRunIdFromResponse(String json) {
        var startIdx = json.indexOf("\"runId\":\"") + 9;
        var endIdx = json.indexOf("\"", startIdx);
        return UUID.fromString(json.substring(startIdx, endIdx));
    }
}
