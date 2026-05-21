package dev.pulsermm.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.identity.api.dto.LoginRequest;
import dev.pulsermm.identity.api.dto.RegisterRequest;
import dev.pulsermm.identity.api.dto.RegisterResponse;
import dev.pulsermm.identity.api.dto.TokenResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("pulse")
        .withUsername("pulse")
        .withPassword("pulse");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("pulse.jwt.secret", () -> "test-jwt-secret-32-chars-long-ok!");
        registry.add("pulse.internal.secret", () -> "test-internal-secret");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDb() {
        jdbc.execute("DELETE FROM identity.users");
    }

    @Test
    void registerFirstUserSucceeds() throws Exception {
        var request = new RegisterRequest("testuser", "testpassword123");

        var result = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.username").value("testuser"))
            .andReturn();

        var response = fromJson(result.getResponse().getContentAsString(), RegisterResponse.class);
        assertThat(response.id()).isNotNull();
    }

    @Test
    void registerDuplicateUserReturnConflict() throws Exception {
        var request = new RegisterRequest("duplicate", "password123456");

        // First registration succeeds
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(request)))
            .andExpect(status().isCreated());

        // Second registration fails with 409
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(request)))
            .andExpect(status().isConflict());
    }

    @Test
    void registerWithInvalidPasswordReturnsBadRequest() throws Exception {
        var request = new RegisterRequest("user", "short");  // Too short

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        // First register
        var registerReq = new RegisterRequest("loginuser", "loginpass123");
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(registerReq)))
            .andExpect(status().isCreated());

        // Then login
        var loginReq = new LoginRequest("loginuser", "loginpass123");
        var result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(loginReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.expiresIn").exists())
            .andExpect(header().exists("Set-Cookie"))
            .andReturn();

        var response = fromJson(result.getResponse().getContentAsString(), TokenResponse.class);
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.expiresIn()).isPositive();
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        // Register
        var registerReq = new RegisterRequest("wrongpass", "correctpass123");
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(registerReq)))
            .andExpect(status().isCreated());

        // Login with wrong password
        var loginReq = new LoginRequest("wrongpass", "incorrectpass123");
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(loginReq)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenReturnsNewToken() throws Exception {
        // Register and login
        var registerReq = new RegisterRequest("refreshuser", "refreshpass123");
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(registerReq)))
            .andExpect(status().isCreated());

        var loginReq = new LoginRequest("refreshuser", "refreshpass123");
        var loginResult = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(loginReq)))
            .andExpect(status().isOk())
            .andReturn();

        // Extract refresh token value from Set-Cookie header
        String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("pulse_refresh");
        String tokenValue = setCookie.split(";")[0].replace("pulse_refresh=", "").trim();

        // Refresh token using cookie
        var refreshResult = mvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("pulse_refresh", tokenValue)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andReturn();

        var response = fromJson(refreshResult.getResponse().getContentAsString(), TokenResponse.class);
        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    void logoutClearsCookie() throws Exception {
        // Register and login
        var registerReq = new RegisterRequest("logoutuser", "logoutpass123");
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(registerReq)))
            .andExpect(status().isCreated());

        var loginReq = new LoginRequest("logoutuser", "logoutpass123");
        var loginResult = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(loginReq)))
            .andExpect(status().isOk())
            .andReturn();

        String tokenValue = loginResult.getResponse().getHeader("Set-Cookie").split(";")[0].replace("pulse_refresh=", "").trim();

        // Logout
        mvc.perform(post("/api/auth/logout")
                .cookie(new Cookie("pulse_refresh", tokenValue)))
            .andExpect(status().isNoContent())
            .andExpect(header().exists("Set-Cookie"));
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String asJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    private <T> T fromJson(String json, Class<T> type) throws Exception {
        return objectMapper.readValue(json, type);
    }
}
