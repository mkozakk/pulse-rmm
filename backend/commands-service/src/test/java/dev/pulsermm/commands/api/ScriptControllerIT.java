package dev.pulsermm.commands.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.commands.api.dto.CreateScriptRequest;
import dev.pulsermm.commands.api.dto.RunScriptRequest;
import dev.pulsermm.commands.infrastructure.persistence.ScriptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ScriptControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("pulse")
        .withUsername("pulse")
        .withPassword("pulse");

    private static final String JWT_SECRET = "test-jwt-secret-32-chars-long-ok!";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("pulse.jwt.secret", () -> JWT_SECRET);
        registry.add("pulse.script.secret-kek", () -> "test-kek-32-chars-long-ok-here!!");
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registry.add("spring.rabbitmq.listener.direct.auto-startup", () -> "false");
        registry.add("pulse.identity.internal-secret", () -> "test-internal-secret");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ScriptRepository scriptRepository;

    @BeforeEach
    void clearScripts() {
        scriptRepository.deleteAll();
    }

    @Test
    void testCreateScript() throws Exception {
        var request = new CreateScriptRequest("test-script", "echo hello");
        mvc.perform(post("/api/scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .content(asJson(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void testCreateScriptMissingName() throws Exception {
        var request = new CreateScriptRequest("", "echo hello");
        mvc.perform(post("/api/scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .content(asJson(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateScriptMissingBody() throws Exception {
        var request = new CreateScriptRequest("script1", "");
        mvc.perform(post("/api/scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .content(asJson(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateScriptNoAuth() throws Exception {
        var request = new CreateScriptRequest("test", "echo test");
        mvc.perform(post("/api/scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetScriptNotFound() throws Exception {
        mvc.perform(get("/api/scripts/" + UUID.randomUUID())
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void testListScriptsEmpty() throws Exception {
        mvc.perform(get("/api/scripts")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scripts").isArray())
            .andExpect(jsonPath("$.scripts.length()").value(0));
    }

    @Test
    void testApproveScriptAlreadyApproved() throws Exception {
        var req = new CreateScriptRequest("test", "echo test");
        var createResp = mvc.perform(post("/api/scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .content(asJson(req)))
            .andExpect(status().isCreated())
            .andReturn();

        String respBody = createResp.getResponse().getContentAsString();
        String scriptIdStr = new ObjectMapper().readTree(respBody).get("id").asText();
        UUID scriptUUID = UUID.fromString(scriptIdStr);

        // Approve once
        mvc.perform(post("/api/scripts/" + scriptUUID + "/approve")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
            .andExpect(status().isOk());

        // Try to approve again
        mvc.perform(post("/api/scripts/" + scriptUUID + "/approve")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
            .andExpect(status().isConflict());
    }

    @Test
    void testRunScriptEmptyEndpoints() throws Exception {
        var createReq = new CreateScriptRequest("test", "echo");
        mvc.perform(post("/api/scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .content(asJson(createReq)))
            .andExpect(status().isCreated());

        var runReq = new RunScriptRequest(List.of(), null);
        mvc.perform(post("/api/scripts/" + UUID.randomUUID() + "/run")
                .contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .content(asJson(runReq)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testRunResultsNotFound() throws Exception {
        mvc.perform(get("/api/scripts/runs/" + UUID.randomUUID() + "/results")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
            .andExpect(status().isNotFound());
    }

    private String asJson(Object obj) throws Exception {
        return new ObjectMapper().writeValueAsString(obj);
    }
}
