package dev.pulsermm.audit.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.audit.domain.AuditEvent;
import dev.pulsermm.audit.infrastructure.persistence.AuditEventRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuditControllerIT {

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

    @Autowired
    private AuditEventRepository auditEventRepository;

    @BeforeEach
    void clearDatabase() {
        auditEventRepository.deleteAll();
    }

    @Test
    void testListEmpty() throws Exception {
        mvc.perform(get("/api/audit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void testListReturnsSavedEvent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        AuditEvent event = new AuditEvent(UUID.randomUUID(), userId, "testuser", "script:run",
            "execute script", endpointId, null, Instant.now());
        auditEventRepository.save(event);

        mvc.perform(get("/api/audit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].action").value("execute script"));
    }

    @Test
    void testListFiltersUserId() throws Exception {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        auditEventRepository.save(new AuditEvent(UUID.randomUUID(), userId1, "user1", "script:run", "action1",
            null, null, Instant.now()));
        auditEventRepository.save(new AuditEvent(UUID.randomUUID(), userId2, "user2", "script:run", "action2",
            null, null, Instant.now()));

        mvc.perform(get("/api/audit?user=" + userId1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].username").value("user1"));
    }

    @Test
    void testListPagination() throws Exception {
        UUID userId = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            auditEventRepository.save(new AuditEvent(UUID.randomUUID(), userId, "user", "script:run", "action" + i,
                null, null, Instant.now()));
        }

        mvc.perform(get("/api/audit?page=0&size=2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void testListRequiresAuth() throws Exception {
        mvc.perform(get("/api/audit"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testExportCsv() throws Exception {
        UUID userId = UUID.randomUUID();
        auditEventRepository.save(new AuditEvent(UUID.randomUUID(), userId, "user", "script:run", "test action",
            null, null, Instant.now()));

        mvc.perform(get("/api/audit/export?format=csv")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/csv"));
    }

    @Test
    void testExportJson() throws Exception {
        UUID userId = UUID.randomUUID();
        auditEventRepository.save(new AuditEvent(UUID.randomUUID(), userId, "user", "script:run", "test action",
            null, null, Instant.now()));

        mvc.perform(get("/api/audit/export?format=json")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/x-ndjson"));
    }

    @Test
    void testDeleteForbidden() throws Exception {
        mvc.perform(delete("/api/audit/anything")
                .header("Authorization", "Bearer " + mintJwt(UUID.randomUUID())))
            .andExpect(status().isForbidden());
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
