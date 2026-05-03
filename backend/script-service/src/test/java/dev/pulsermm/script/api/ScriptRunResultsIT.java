package dev.pulsermm.script.api;

import dev.pulsermm.script.domain.ScriptRun;
import dev.pulsermm.script.domain.ScriptRunResult;
import dev.pulsermm.script.infrastructure.persistence.ScriptRunRepository;
import dev.pulsermm.script.infrastructure.persistence.ScriptRunResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ScriptRunResultsIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScriptRunRepository scriptRunRepository;

    @Autowired
    private ScriptRunResultRepository scriptRunResultRepository;

    private UUID runId;

    @BeforeEach
    void setUp() {
        var run = new ScriptRun(UUID.randomUUID(), UUID.randomUUID());
        var savedRun = scriptRunRepository.save(run);
        runId = savedRun.getId();

        var endpoint1 = UUID.randomUUID();
        var endpoint2 = UUID.randomUUID();

        var result1 = new ScriptRunResult(runId, endpoint1);
        var result2 = new ScriptRunResult(runId, endpoint2);
        result2.setExitCode(0);
        result2.setOutput("success");
        result2.setAckedAt(OffsetDateTime.now());

        scriptRunResultRepository.save(result1);
        scriptRunResultRepository.save(result2);
    }

    @Test
    void getScriptRunResults_ReturnsAllResults() throws Exception {
        mockMvc.perform(get("/api/scripts/runs/{runId}/results", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.pending").value(1));
    }

    @Test
    void getScriptRunResults_IncludesCompletedResults() throws Exception {
        mockMvc.perform(get("/api/scripts/runs/{runId}/results", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.exitCode == 0)].output").value("success"));
    }

    @Test
    void getScriptRunResults_Returns404_ForNonExistentRun() throws Exception {
        var randomId = UUID.randomUUID();
        mockMvc.perform(get("/api/scripts/runs/{runId}/results", randomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCRIPT_RUN_NOT_FOUND"));
    }
}
