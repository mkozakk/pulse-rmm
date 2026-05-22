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

    private String shTemplate;
    private String ps1Template;

    public InstallScriptController(EnrolmentTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @PostConstruct
    void loadTemplates() throws IOException {
        shTemplate = new ClassPathResource("templates/install.sh.template")
                .getContentAsString(StandardCharsets.UTF_8);
        ps1Template = new ClassPathResource("templates/install.ps1.template")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/install/{tokenId}.sh", produces = "text/x-shellscript")
    public ResponseEntity<String> getShScript(@PathVariable String tokenId) {
        return renderScript(tokenId, shTemplate, "text/x-shellscript");
    }

    @GetMapping(value = "/install/{tokenId}.ps1", produces = "text/plain")
    public ResponseEntity<String> getPs1Script(@PathVariable String tokenId) {
        return renderScript(tokenId, ps1Template, "text/plain");
    }

    private ResponseEntity<String> renderScript(String tokenId, String template, String contentType) {
        UUID id;
        try {
            id = UUID.fromString(tokenId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }

        var token = tokenRepository.findLive(id, Instant.now());
        if (token.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String script = template
                .replace("{{API_URL}}", apiUrl)
                .replace("{{TOKEN}}", tokenId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(script);
    }
}
