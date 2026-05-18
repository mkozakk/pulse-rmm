package dev.pulsermm.alert.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.alert.api.dto.CreateAlertRuleRequest;
import dev.pulsermm.alert.api.dto.AlertRuleResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AlertControllerIT {

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
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registry.add("spring.rabbitmq.listener.direct.auto-startup", () -> "false");
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void testCreateRuleReturns201() throws Exception {
        var request = new CreateAlertRuleRequest("cpu-high", "cpu", ">", 80.0, 300,
            new CreateAlertRuleRequest.TargetSpec("group", "all"));

        var result = mvc.perform(post("/api/alert-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("cpu-high"))
            .andExpect(header().exists("Location"))
            .andReturn();

        var response = fromJson(result.getResponse().getContentAsString(), AlertRuleResponse.class);
        assertThat(response.id()).isNotNull();
    }

    @Test
    void testCreateRuleNoAuth() throws Exception {
        var request = new CreateAlertRuleRequest("test-rule", "cpu", ">", 50.0, 300,
            new CreateAlertRuleRequest.TargetSpec("group", "default"));

        mvc.perform(post("/api/alert-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testCreateRuleInvalidMetricType() throws Exception {
        var request = new CreateAlertRuleRequest("test", "memory", ">", 50.0, 300,
            new CreateAlertRuleRequest.TargetSpec("group", "all"));

        mvc.perform(post("/api/alert-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateRuleThresholdOver100() throws Exception {
        var request = new CreateAlertRuleRequest("test", "cpu", ">", 150.0, 300,
            new CreateAlertRuleRequest.TargetSpec("group", "all"));

        mvc.perform(post("/api/alert-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateRuleNegativeThreshold() throws Exception {
        var request = new CreateAlertRuleRequest("test", "cpu", ">", -5.0, 300,
            new CreateAlertRuleRequest.TargetSpec("group", "all"));

        mvc.perform(post("/api/alert-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateRuleDurationTooShort() throws Exception {
        var request = new CreateAlertRuleRequest("test", "cpu", ">", 50.0, 10,
            new CreateAlertRuleRequest.TargetSpec("group", "all"));

        mvc.perform(post("/api/alert-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateRuleMissingName() throws Exception {
        var request = new CreateAlertRuleRequest("", "cpu", ">", 50.0, 300,
            new CreateAlertRuleRequest.TargetSpec("group", "all"));

        mvc.perform(post("/api/alert-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testListRulesEmpty() throws Exception {
        mvc.perform(get("/api/alert-rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0]").doesNotExist());
    }

    @Test
    void testListRulesAfterCreate() throws Exception {
        // Create a rule
        var request = new CreateAlertRuleRequest("disk-rule", "disk", "<", 20.0, 300,
            new CreateAlertRuleRequest.TargetSpec("group", "all"));

        mvc.perform(post("/api/alert-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(request)))
            .andExpect(status().isCreated());

        // Then list
        mvc.perform(get("/api/alert-rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("disk-rule"))
            .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    void testDeleteRuleReturns204() throws Exception {
        // Create a rule
        var createReq = new CreateAlertRuleRequest("todelete", "cpu", ">", 75.0, 300,
            new CreateAlertRuleRequest.TargetSpec("group", "all"));

        var createResult = mvc.perform(post("/api/alert-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(createReq)))
            .andExpect(status().isCreated())
            .andReturn();

        var response = fromJson(createResult.getResponse().getContentAsString(), AlertRuleResponse.class);
        UUID ruleId = response.id();

        // Delete it
        mvc.perform(delete("/api/alert-rules/" + ruleId))
            .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteRuleNotFound() throws Exception {
        mvc.perform(delete("/api/alert-rules/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void testListAlertsEmpty() throws Exception {
        mvc.perform(get("/api/alerts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0]").doesNotExist());
    }

    @Test
    void testListAlertsAllStatus() throws Exception {
        mvc.perform(get("/api/alerts?status=all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testAckAlertNoAuth() throws Exception {
        mvc.perform(post("/api/alerts/" + UUID.randomUUID() + "/ack"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testAckAlertNotFound() throws Exception {
        mvc.perform(post("/api/alerts/" + UUID.randomUUID() + "/ack")
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String asJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    private <T> T fromJson(String json, Class<T> type) throws Exception {
        return objectMapper.readValue(json, type);
    }
}
