package dev.pulsermm.enrolment.api.controller;

import dev.pulsermm.enrolment.infrastructure.EnrolmentTokenRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@RestController
public class InstallScriptController {

    private final EnrolmentTokenRepository tokenRepository;

    @Value("${pulse.api.url:http://localhost:8080}")
    private String apiUrl;

    private String scriptTemplate;

    public InstallScriptController(EnrolmentTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @PostConstruct
    void loadTemplate() throws IOException {
        var resource = new ClassPathResource("templates/install.sh.template");
        scriptTemplate = resource.getContentAsString(StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/install/{tokenId}.sh", produces = "text/x-shellscript")
    public ResponseEntity<String> getInstallScript(@PathVariable String tokenId) {
        UUID id;
        try {
            id = UUID.fromString(tokenId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }

        var token = tokenRepository.findByIdAndRevokedFalseAndExpiresAtAfter(id, Instant.now());
        if (token.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String script = scriptTemplate
                .replace("{{API_URL}}", apiUrl)
                .replace("{{TOKEN}}", tokenId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/x-shellscript"))
                .body(script);
    }
}
