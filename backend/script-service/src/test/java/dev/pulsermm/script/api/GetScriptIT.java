package dev.pulsermm.script.api;

import dev.pulsermm.script.domain.Script;
import dev.pulsermm.script.infrastructure.persistence.ScriptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class GetScriptIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScriptRepository scriptRepository;

    private UUID scriptId;

    @BeforeEach
    void setUp() {
        var script = new Script("Test Script", "echo hello", UUID.randomUUID());
        var saved = scriptRepository.save(script);
        scriptId = saved.getId();
    }

    @Test
    void getScript_ReturnsScriptWithAllFields() throws Exception {
        mockMvc.perform(get("/api/scripts/{id}", scriptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(scriptId.toString()))
                .andExpect(jsonPath("$.name").value("Test Script"))
                .andExpect(jsonPath("$.body").value("echo hello"))
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.approvedAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void getScript_ReturnsApprovedStatus_WhenScriptIsApproved() throws Exception {
        var script = scriptRepository.findById(scriptId).orElseThrow();
        script.setApprovedAt(OffsetDateTime.now());
        scriptRepository.save(script);

        mockMvc.perform(get("/api/scripts/{id}", scriptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.approvedAt").exists());
    }

    @Test
    void getScript_Returns404_ForNonExistentScript() throws Exception {
        var randomId = UUID.randomUUID();
        mockMvc.perform(get("/api/scripts/{id}", randomId))
                .andExpect(status().isNotFound());
    }
}
