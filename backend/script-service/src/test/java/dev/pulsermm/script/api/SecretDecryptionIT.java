package dev.pulsermm.script.api;

import dev.pulsermm.script.application.ScriptService;
import dev.pulsermm.script.domain.Script;
import dev.pulsermm.script.infrastructure.persistence.ScriptRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
@Disabled("Requires Docker/Podman - tested via e2e")
class SecretDecryptionIT {

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
    private ScriptService scriptService;

    private UUID scriptId;
    private UUID userId;
    private UUID runId;

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID();
        var endpoint1 = UUID.randomUUID();

        var script = new Script("Decrypt Test Script", "echo test", userId);
        script.setApprovedAt(OffsetDateTime.now());
        scriptId = scriptRepository.save(script).getId();

        var requestBody = """
                {
                  "endpointIds": ["%s"],
                  "secrets": {
                    "DB_PASSWORD": "super-secret-password",
                    "API_KEY": "secret-api-key-12345",
                    "AUTH_TOKEN": "bearer-token-xyz"
                  }
                }
                """.formatted(endpoint1);

        var response = mockMvc.perform(post("/api/scripts/{id}/run", scriptId)
                .with(request -> {
                    request.setUserPrincipal(() -> userId.toString());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isAccepted())
                .andReturn();

        runId = extractRunIdFromResponse(response.getResponse().getContentAsString());
    }

    @Test
    void agentCanDecryptSecrets_RetrievesAllPlaintexts() {
        var decrypted = scriptService.getDecryptedSecretsForRun(runId);

        assertThat(decrypted).hasSize(3);
        assertThat(decrypted.get("DB_PASSWORD")).isEqualTo("super-secret-password");
        assertThat(decrypted.get("API_KEY")).isEqualTo("secret-api-key-12345");
        assertThat(decrypted.get("AUTH_TOKEN")).isEqualTo("bearer-token-xyz");
    }

    @Test
    void agentDecryption_PreservesExactPlaintexts() {
        var decrypted = scriptService.getDecryptedSecretsForRun(runId);

        assertThat(decrypted.get("DB_PASSWORD"))
                .isEqualTo("super-secret-password")
                .doesNotContain("\n")
                .doesNotContain("\t");
    }

    @Test
    void agentDecryption_WithEmptySecrets() throws Exception {
        var endpoint1 = UUID.randomUUID();
        var requestBody = """
                {
                  "endpointIds": ["%s"]
                }
                """.formatted(endpoint1);

        var response = mockMvc.perform(post("/api/scripts/{id}/run", scriptId)
                .with(request -> {
                    request.setUserPrincipal(() -> userId.toString());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isAccepted())
                .andReturn();

        var runIdNoSecrets = extractRunIdFromResponse(response.getResponse().getContentAsString());
        var decrypted = scriptService.getDecryptedSecretsForRun(runIdNoSecrets);

        assertThat(decrypted).isEmpty();
    }

    private UUID extractRunIdFromResponse(String json) {
        var startIdx = json.indexOf("\"runId\":\"") + 9;
        var endIdx = json.indexOf("\"", startIdx);
        return UUID.fromString(json.substring(startIdx, endIdx));
    }
}
