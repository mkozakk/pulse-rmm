package dev.pulsermm.commands.api;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SoftwareControllerIT {

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

    @Test
    void testListSoftwareEmpty() throws Exception {
        UUID endpointId = UUID.randomUUID();
        mvc.perform(get("/api/endpoints/" + endpointId + "/software")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0]").doesNotExist());
    }

    @Test
    void testListSoftwareRequiresAuth() throws Exception {
        UUID endpointId = UUID.randomUUID();
        mvc.perform(get("/api/endpoints/" + endpointId + "/software"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testInstallReturns201() throws Exception {
        UUID endpointId = UUID.randomUUID();
        var installRequest = new ObjectMapper().createObjectNode();
        installRequest.put("name", "curl");

        mvc.perform(post("/api/endpoints/" + endpointId + "/software/install")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(installRequest.toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void testUpdateReturns201() throws Exception {
        UUID endpointId = UUID.randomUUID();
        var updateRequest = new ObjectMapper().createObjectNode();
        updateRequest.put("name", "vim");

        mvc.perform(post("/api/endpoints/" + endpointId + "/software/update")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(updateRequest.toString()))
            .andExpect(status().isCreated());
    }

    @Test
    void testRemoveReturns201() throws Exception {
        UUID endpointId = UUID.randomUUID();
        var removeRequest = new ObjectMapper().createObjectNode();
        removeRequest.put("name", "nano");

        mvc.perform(post("/api/endpoints/" + endpointId + "/software/remove")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID()))
                .content(removeRequest.toString()))
            .andExpect(status().isCreated());
    }

    @Test
    void testSoftwareCommandBasics() throws Exception {
        // Software commands tested via install/update/remove above
        UUID endpointId = UUID.randomUUID();
        mvc.perform(get("/api/endpoints/" + endpointId + "/software")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isOk());
    }

    private String mintJwt(UUID userId) {
        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3_600_000))
            .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }
}
