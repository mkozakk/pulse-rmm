package dev.pulsermm.script.api;

import dev.pulsermm.script.infrastructure.persistence.ScriptRepository;
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
class CreateScriptIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScriptRepository scriptRepository;

    @Test
    void createScript_StoresWithCorrectMetadata() throws Exception {
        var userId = UUID.randomUUID().toString();
        var requestBody = """
                {
                  "name": "Test Script",
                  "body": "echo hello"
                }
                """;

        mockMvc.perform(post("/api/scripts")
                .with(request -> {
                    request.setUserPrincipal(() -> userId);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());

        var scripts = scriptRepository.findAll();
        assertThat(scripts).hasSize(1);
        assertThat(scripts.get(0).getName()).isEqualTo("Test Script");
        assertThat(scripts.get(0).getBody()).isEqualTo("echo hello");
        assertThat(scripts.get(0).getCreatedBy()).isEqualTo(UUID.fromString(userId));
        assertThat(scripts.get(0).getApprovedAt()).isNull();
    }

    @Test
    void createScript_ValidatesRequiredFields() throws Exception {
        var requestBody = """
                {
                  "name": "",
                  "body": ""
                }
                """;

        mockMvc.perform(post("/api/scripts")
                .with(request -> {
                    request.setUserPrincipal(() -> UUID.randomUUID().toString());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createScript_RequiresAuthentication() throws Exception {
        var requestBody = """
                {
                  "name": "Test Script",
                  "body": "echo hello"
                }
                """;

        mockMvc.perform(post("/api/scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isUnauthorized());
    }
}
