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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class ScriptExecutionIT {

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
        var script = new Script("Test Script", "echo hello", userId);
        script.setApprovedAt(OffsetDateTime.now());
        scriptId = scriptRepository.save(script).getId();
    }

    @Test
    void runScript_CreatesScriptRunAndResults() throws Exception {
        var endpoint1 = UUID.randomUUID();
        var endpoint2 = UUID.randomUUID();
        var requestBody = """
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
                .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").exists())
                .andReturn();

        var runId = extractRunIdFromResponse(response.getResponse().getContentAsString());

        var results = scriptRunResultRepository.findByRunId(runId);
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.isPending());
    }

    @Test
    void runScript_RequiresAuthentication() throws Exception {
        var requestBody = """
                {
                  "endpointIds": ["550e8400-e29b-41d4-a716-446655440000"],
                  "secrets": {}
                }
                """;

        mockMvc.perform(post("/api/scripts/{id}/run", scriptId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void runScript_ValidatesEndpointIds() throws Exception {
        var requestBody = """
                {
                  "endpointIds": [],
                  "secrets": {}
                }
                """;

        mockMvc.perform(post("/api/scripts/{id}/run", scriptId)
                .with(request -> {
                    request.setUserPrincipal(() -> userId.toString());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    private UUID extractRunIdFromResponse(String json) {
        // Simple JSON parsing
        var startIdx = json.indexOf("\"runId\":\"") + 9;
        var endIdx = json.indexOf("\"", startIdx);
        return UUID.fromString(json.substring(startIdx, endIdx));
    }
}
