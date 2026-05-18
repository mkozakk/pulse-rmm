package dev.pulsermm.integration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.integration.api.dto.CreateWebhookRequest;
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
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class WebhookControllerIT {

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
        registry.add("pulse.webhooks.kek", () -> "test-kek-32-chars-long-ok-here!!");
        registry.add("pulse.webhooks.allow-http", () -> "true");
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registry.add("spring.rabbitmq.listener.direct.auto-startup", () -> "false");
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void testCreateWebhookReturns201() throws Exception {
        var request = new CreateWebhookRequest("http://localhost:9000/webhook",
            List.of("alert.triggered"), "secret-key-16-chars!");

        var result = mvc.perform(post("/api/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(header().exists("Location"))
            .andReturn();
    }

    @Test
    void testCreateWebhookNoAuth() throws Exception {
        var request = new CreateWebhookRequest("http://localhost:9000/hook",
            List.of("alert.triggered"), "secret-key-16-chars!");

        mvc.perform(post("/api/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testCreateWebhookShortSecret() throws Exception {
        var request = new CreateWebhookRequest("http://localhost:9000/hook",
            List.of("alert.triggered"), "short");

        mvc.perform(post("/api/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateWebhookEmptyEventTypes() throws Exception {
        var request = new CreateWebhookRequest("http://localhost:9000/hook",
            List.of(), "secret-key-16-chars!");

        mvc.perform(post("/api/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateWebhookBlankUrl() throws Exception {
        var request = new CreateWebhookRequest("",
            List.of("alert.triggered"), "secret-key-16-chars!");

        mvc.perform(post("/api/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(asJson(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testListWebhooksEmpty() throws Exception {
        mvc.perform(get("/api/webhooks")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0]").doesNotExist());
    }

    @Test
    void testGetWebhookNotFound() throws Exception {
        mvc.perform(get("/api/webhooks/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteWebhookNotFound() throws Exception {
        mvc.perform(delete("/api/webhooks/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isNotFound());
    }

    @Test
    void testListDeliveriesEmpty() throws Exception {
        UUID webhookId = UUID.randomUUID();
        mvc.perform(get("/api/webhooks/" + webhookId + "/deliveries")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testDeadLetterEmpty() throws Exception {
        mvc.perform(get("/api/webhooks/deliveries/dead-letter")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
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
