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
class ApproveScriptIT {

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
    void approveScript_SetsApprovedAt_AndReturnsScript() throws Exception {
        mockMvc.perform(post("/api/scripts/{id}/approve", scriptId)
                .with(request -> {
                    request.setUserPrincipal(() -> UUID.randomUUID().toString());
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(scriptId.toString()))
                .andExpect(jsonPath("$.name").value("Test Script"))
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.approvedAt").exists());

        var script = scriptRepository.findById(scriptId).orElseThrow();
        assertThat(script.getApprovedAt()).isNotNull();
    }

    @Test
    void approveScript_RequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/scripts/{id}/approve", scriptId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approveScript_FailsIfAlreadyApproved() throws Exception {
        var script = scriptRepository.findById(scriptId).orElseThrow();
        script.setApprovedAt(OffsetDateTime.now());
        scriptRepository.save(script);

        mockMvc.perform(post("/api/scripts/{id}/approve", scriptId)
                .with(request -> {
                    request.setUserPrincipal(() -> UUID.randomUUID().toString());
                    return request;
                }))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCRIPT_ALREADY_APPROVED"));
    }

    @Test
    void approveScript_Returns404_ForNonExistentScript() throws Exception {
        var randomId = UUID.randomUUID();
        mockMvc.perform(post("/api/scripts/{id}/approve", randomId)
                .with(request -> {
                    request.setUserPrincipal(() -> UUID.randomUUID().toString());
                    return request;
                }))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCRIPT_NOT_FOUND"));
    }
}
