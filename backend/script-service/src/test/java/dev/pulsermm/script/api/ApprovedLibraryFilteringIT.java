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

import java.util.UUID;

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
        "spring.datasource.password=testpass"
})
class ApprovedLibraryFilteringIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScriptRepository scriptRepository;

    private UUID pendingScriptId;
    private UUID approvedScriptId;

    @BeforeEach
    void setUp() {
        scriptRepository.deleteAll();

        var pending = new Script("Pending Script", "echo pending", UUID.randomUUID());
        pendingScriptId = scriptRepository.save(pending).getId();

        var approved = new Script("Approved Script", "echo approved", UUID.randomUUID());
        approvedScriptId = scriptRepository.save(approved).getId();
    }

    @Test
    void afterApproval_ScriptAppearsInLibraryFilter() throws Exception {
        // Approve the script
        mockMvc.perform(post("/api/scripts/{id}/approve", approvedScriptId)
                .with(request -> {
                    request.setUserPrincipal(() -> UUID.randomUUID().toString());
                    return request;
                }))
                .andExpect(status().isOk());

        // Verify it appears in library filter
        mockMvc.perform(get("/api/scripts?status=library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scripts.length()").value(1))
                .andExpect(jsonPath("$.scripts[0].id").value(approvedScriptId.toString()))
                .andExpect(jsonPath("$.scripts[0].approved").value(true))
                .andExpect(jsonPath("$.total").value(1));

        // Verify pending still in pending filter
        mockMvc.perform(get("/api/scripts?status=pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scripts.length()").value(1))
                .andExpect(jsonPath("$.scripts[0].id").value(pendingScriptId.toString()))
                .andExpect(jsonPath("$.scripts[0].approved").value(false))
                .andExpect(jsonPath("$.total").value(1));

        // Verify both in all filter
        mockMvc.perform(get("/api/scripts?status=all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scripts.length()").value(2))
                .andExpect(jsonPath("$.total").value(2));
    }
}
