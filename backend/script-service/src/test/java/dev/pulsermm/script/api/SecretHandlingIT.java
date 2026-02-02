package dev.pulsermm.script.api;

import dev.pulsermm.script.domain.Script;
import dev.pulsermm.script.infrastructure.persistence.ScriptRepository;
import dev.pulsermm.script.infrastructure.persistence.ScriptSecretRepository;
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
class SecretHandlingIT {

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
    private ScriptSecretRepository scriptSecretRepository;

    private UUID scriptId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        var script = new Script("Secret Test Script", "echo test", userId);
        script.setApprovedAt(OffsetDateTime.now());
        scriptId = scriptRepository.save(script).getId();
    }

    @Test
    void runScript_EncryptsSecretsBeforeStoring() throws Exception {
        var endpoint1 = UUID.randomUUID();
        var plainSecret = "super-secret-database-password";

        var requestBody = """
                {
                  "endpointIds": ["%s"],
                  "secrets": {"DB_PASSWORD": "%s"}
                }
                """.formatted(endpoint1, plainSecret);

        mockMvc.perform(post("/api/scripts/{id}/run", scriptId)
                .with(request -> {
                    request.setUserPrincipal(() -> userId.toString());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isAccepted());

        var savedSecrets = scriptSecretRepository.findAll();
        assertThat(savedSecrets).hasSize(1);

        var savedSecret = savedSecrets.get(0);
        assertThat(savedSecret.getKey()).isEqualTo("DB_PASSWORD");
        assertThat(savedSecret.getEncryptedValue()).isNotEqualTo(plainSecret);
        assertThat(savedSecret.getEncryptedValue()).doesNotContain(plainSecret);
    }

    @Test
    void runScript_MultipleSecrets_AllEncrypted() throws Exception {
        var endpoint1 = UUID.randomUUID();

        var requestBody = """
                {
                  "endpointIds": ["%s"],
                  "secrets": {
                    "DB_PASSWORD": "secret-password",
                    "API_KEY": "secret-api-key",
                    "AUTH_TOKEN": "secret-auth-token"
                  }
                }
                """.formatted(endpoint1);

        mockMvc.perform(post("/api/scripts/{id}/run", scriptId)
                .with(request -> {
                    request.setUserPrincipal(() -> userId.toString());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isAccepted());

        var savedSecrets = scriptSecretRepository.findAll();
        assertThat(savedSecrets).hasSize(3);

        assertThat(savedSecrets).allMatch(s ->
                !s.getEncryptedValue().contains("secret-password") &&
                !s.getEncryptedValue().contains("secret-api-key") &&
                !s.getEncryptedValue().contains("secret-auth-token")
        );
    }

    @Test
    void runScript_NoSecrets_WorksCorrectly() throws Exception {
        var endpoint1 = UUID.randomUUID();

        var requestBody = """
                {
                  "endpointIds": ["%s"]
                }
                """.formatted(endpoint1);

        mockMvc.perform(post("/api/scripts/{id}/run", scriptId)
                .with(request -> {
                    request.setUserPrincipal(() -> userId.toString());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isAccepted());

        var savedSecrets = scriptSecretRepository.findAll();
        assertThat(savedSecrets).isEmpty();
    }
}
