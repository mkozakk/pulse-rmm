package dev.pulsermm.rbac.infrastructure.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class KeycloakAdminClient {

    private final RestClient restClient;
    private final String realm = "pulse-rmm";
    private final String adminUsername;
    private final String adminPassword;

    private volatile String token;

    public KeycloakAdminClient(
            @Value("${keycloak.url}") String keycloakUrl,
            @Value("${keycloak.admin.username}") String adminUsername,
            @Value("${keycloak.admin.password}") String adminPassword) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.restClient = RestClient.builder()
            .baseUrl(keycloakUrl)
            .build();
    }

    public List<KeycloakUser> listUsers() {
        return withToken(tok -> {
            var users = restClient.get()
                .uri("/admin/realms/{realm}/users", realm)
                .header("Authorization", "Bearer " + tok)
                .retrieve()
                .body(KeycloakUserJson[].class);
            return users == null ? List.of() : Arrays.stream(users).map(KeycloakUserJson::toRecord).toList();
        });
    }

    // Keycloak attribute search: ?q=org_id:<uuid> matches users carrying that custom attribute.
    public List<KeycloakUser> listUsersByOrg(UUID orgId) {
        return withToken(tok -> {
            var users = restClient.get()
                .uri(b -> b.path("/admin/realms/{realm}/users").queryParam("q", "org_id:" + orgId).build(realm))
                .header("Authorization", "Bearer " + tok)
                .retrieve()
                .body(KeycloakUserJson[].class);
            return users == null ? List.of() : Arrays.stream(users).map(KeycloakUserJson::toRecord).toList();
        });
    }

    public UUID createUser(String username, String email, String firstName, String lastName, String password) {
        return createUser(username, email, firstName, lastName, password, null);
    }

    public UUID createUser(String username, String email, String firstName, String lastName, String password, UUID orgId) {
        var body = new java.util.HashMap<String, Object>();
        body.put("username", username);
        body.put("email", email != null ? email : "");
        body.put("firstName", firstName != null ? firstName : "");
        body.put("lastName", lastName != null ? lastName : "");
        body.put("enabled", true);
        body.put("credentials", List.of(Map.of("type", "password", "value", password, "temporary", false)));
        if (orgId != null) {
            body.put("attributes", Map.of("org_id", List.of(orgId.toString())));
        }
        withToken(tok -> {
            restClient.post()
                .uri("/admin/realms/{realm}/users", realm)
                .header("Authorization", "Bearer " + tok)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            return null;
        });
        // Fetch the created user's UUID
        var found = listUsers().stream()
            .filter(u -> u.username().equals(username))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("User not found after creation: " + username));
        return found.id();
    }

    public KeycloakUser getUser(UUID userId) {
        return withToken(tok -> {
            var user = restClient.get()
                .uri("/admin/realms/{realm}/users/{id}", realm, userId)
                .header("Authorization", "Bearer " + tok)
                .retrieve()
                .body(KeycloakUserJson.class);
            if (user == null) throw new RuntimeException("User not found: " + userId);
            return user.toRecord();
        });
    }

    public void updateUser(UUID userId, String email, String firstName, String lastName, Boolean enabled) {
        var body = new java.util.HashMap<String, Object>();
        if (email != null) body.put("email", email);
        if (firstName != null) body.put("firstName", firstName);
        if (lastName != null) body.put("lastName", lastName);
        if (enabled != null) body.put("enabled", enabled);

        withToken(tok -> {
            restClient.put()
                .uri("/admin/realms/{realm}/users/{id}", realm, userId)
                .header("Authorization", "Bearer " + tok)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            return null;
        });
    }

    public void deleteUser(UUID userId) {
        withToken(tok -> {
            restClient.delete()
                .uri("/admin/realms/{realm}/users/{id}", realm, userId)
                .header("Authorization", "Bearer " + tok)
                .retrieve()
                .toBodilessEntity();
            return null;
        });
    }

    public void resetPassword(UUID userId, String newPassword) {
        var cred = Map.of("type", "password", "value", newPassword, "temporary", false);
        withToken(tok -> {
            restClient.put()
                .uri("/admin/realms/{realm}/users/{id}/reset-password", realm, userId)
                .header("Authorization", "Bearer " + tok)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cred)
                .retrieve()
                .toBodilessEntity();
            return null;
        });
    }

    private <T> T withToken(java.util.function.Function<String, T> action) {
        if (token == null) {
            token = fetchToken();
        }
        try {
            return action.apply(token);
        } catch (HttpClientErrorException.Unauthorized e) {
            token = fetchToken();
            return action.apply(token);
        }
    }

    private String fetchToken() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", "admin-cli");
        form.add("username", adminUsername);
        form.add("password", adminPassword);
        form.add("grant_type", "password");

        var response = restClient.post()
            .uri("/realms/master/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(TokenResponse.class);

        if (response == null || response.access_token() == null) {
            throw new RuntimeException("Failed to obtain Keycloak admin token");
        }
        return response.access_token();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(String access_token) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KeycloakUserJson(String id, String username, String email, String firstName, String lastName,
                            boolean enabled, Map<String, List<String>> attributes) {
        KeycloakUser toRecord() {
            UUID orgId = null;
            if (attributes != null) {
                var values = attributes.get("org_id");
                if (values != null && !values.isEmpty() && values.get(0) != null && !values.get(0).isBlank()) {
                    orgId = UUID.fromString(values.get(0));
                }
            }
            return new KeycloakUser(UUID.fromString(id), username, email, firstName, lastName, enabled, orgId);
        }
    }
}
