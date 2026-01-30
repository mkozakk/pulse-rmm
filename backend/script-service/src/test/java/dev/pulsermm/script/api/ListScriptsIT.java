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
class ListScriptsIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScriptRepository scriptRepository;

    @BeforeEach
    void setUp() {
        scriptRepository.deleteAll();

        var pending1 = new Script("Pending Script 1", "echo 1", UUID.randomUUID());
        var pending2 = new Script("Pending Script 2", "echo 2", UUID.randomUUID());
        scriptRepository.save(pending1);
        scriptRepository.save(pending2);

        var approved = new Script("Approved Script", "echo approved", UUID.randomUUID());
        approved.setApprovedAt(OffsetDateTime.now());
        scriptRepository.save(approved);
    }

    @Test
    void listScripts_FilterByPending_ReturnsOnlyUnapprovedScripts() throws Exception {
        mockMvc.perform(get("/api/scripts?status=pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scripts.length()").value(2))
                .andExpect(jsonPath("$.scripts[0].approved").value(false))
                .andExpect(jsonPath("$.scripts[1].approved").value(false))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void listScripts_FilterByLibrary_ReturnsOnlyApprovedScripts() throws Exception {
        mockMvc.perform(get("/api/scripts?status=library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scripts.length()").value(1))
                .andExpect(jsonPath("$.scripts[0].approved").value(true))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void listScripts_FilterByAll_ReturnsAllScripts() throws Exception {
        mockMvc.perform(get("/api/scripts?status=all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scripts.length()").value(3))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void listScripts_DefaultsToAll_WhenStatusNotSpecified() throws Exception {
        mockMvc.perform(get("/api/scripts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scripts.length()").value(3))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void listScripts_ReturnsPaginated_WithCustomPageSize() throws Exception {
        mockMvc.perform(get("/api/scripts?status=all&page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scripts.length()").value(2))
                .andExpect(jsonPath("$.total").value(3));
    }
}
