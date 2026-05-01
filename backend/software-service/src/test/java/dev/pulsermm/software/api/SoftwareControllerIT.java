package dev.pulsermm.software.api;

import dev.pulsermm.software.application.SoftwareService;
import dev.pulsermm.software.infrastructure.SoftwareCommandRepository;
import dev.pulsermm.software.infrastructure.SoftwareItemRepository;
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

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class SoftwareControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("pulse")
        .withUsername("pulse")
        .withPassword("pulse");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SoftwareItemRepository softwareItemRepository;

    @Autowired
    private SoftwareCommandRepository softwareCommandRepository;

    @Autowired
    private SoftwareService softwareService;

    private UUID endpointId;
    private String jwtToken;
    private final String jwtSecret = "a-secret-key-that-is-long-enough-for-hs256-algorithm-256-bits-minimum";

    @BeforeEach
    void setUp() {
        endpointId = UUID.randomUUID();
        softwareItemRepository.deleteAll();
        softwareCommandRepository.deleteAll();
        jwtToken = generateToken();
    }

    @Test
    void testGetSoftwareListReturnsEmpty() throws Exception {
        mockMvc.perform(get("/api/endpoints/{endpointId}/software", endpointId)
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testGetSoftwareListReturnsItems() throws Exception {
        softwareService.storeSoftwareList(endpointId, java.util.List.of(
            new SoftwareService.SoftwareItemDTO("curl", "7.0", "apt"),
            new SoftwareService.SoftwareItemDTO("git", "2.34", "apt")
        ));

        mockMvc.perform(get("/api/endpoints/{endpointId}/software", endpointId)
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].name").value("curl"))
            .andExpect(jsonPath("$[0].version").value("7.0"))
            .andExpect(jsonPath("$[0].source").value("apt"));
    }

    @Test
    void testInstallCommandCreated() throws Exception {
        mockMvc.perform(post("/api/endpoints/{endpointId}/software/install", endpointId)
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"curl\", \"version\": \"7.0\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void testRemoveCommandCreated() throws Exception {
        mockMvc.perform(post("/api/endpoints/{endpointId}/software/remove", endpointId)
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"curl\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void testUnauthorizedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/endpoints/{endpointId}/software", endpointId))
            .andExpect(status().isUnauthorized());
    }

    private String generateToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
            .subject("testuser")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(key)
            .compact();
    }
}
