package dev.pulsermm.commands.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.commands.api.dto.CreateScriptRequest;
import dev.pulsermm.commands.api.dto.RunScriptRequest;
import dev.pulsermm.commands.infrastructure.persistence.ScriptRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void testCreateScriptMissingName() throws Exception {
        var request = new CreateScriptRequest("", "echo hello");
        mvc.perform(post("/api/scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateScriptMissingBody() throws Exception {
        var request = new CreateScriptRequest("script1", "");
        mvc.perform(post("/api/scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
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
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isNotFound());
    }

    @Test
    void testListScriptsEmpty() throws Exception {
        mvc.perform(get("/api/scripts")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scripts").isArray())
            .andExpect(jsonPath("$.scripts.length()").value(0));
    }

    @Test
    void testApproveScriptAlreadyApproved() throws Exception {
        var req = new CreateScriptRequest("test", "echo test");
        var createResp = mvc.perform(post("/api/scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(req)))
            .andExpect(status().isCreated())
            .andReturn();

        String respBody = createResp.getResponse().getContentAsString();
        String scriptIdStr = new ObjectMapper().readTree(respBody).get("id").asText();
        UUID scriptUUID = UUID.fromString(scriptIdStr);

        // Approve once
        mvc.perform(post("/api/scripts/" + scriptUUID + "/approve")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isOk());

        // Try to approve again
        mvc.perform(post("/api/scripts/" + scriptUUID + "/approve")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isConflict());
    }

    @Test
    void testRunScriptEmptyEndpoints() throws Exception {
        var createReq = new CreateScriptRequest("test", "echo");
        mvc.perform(post("/api/scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(createReq)))
            .andExpect(status().isCreated());

        var runReq = new RunScriptRequest(List.of(), null);
        mvc.perform(post("/api/scripts/" + UUID.randomUUID() + "/run")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(runReq)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testRunResultsNotFound() throws Exception {
        mvc.perform(get("/api/scripts/runs/" + UUID.randomUUID() + "/results")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isNotFound());
    }

    private String mintJwt(UUID userId) {
        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3_600_000))
            .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }

    private String asJson(Object obj) throws Exception {
        return new ObjectMapper().writeValueAsString(obj);
    }
}
