package dev.pulsermm.script.api;

import dev.pulsermm.script.application.ScriptService;
import dev.pulsermm.script.application.ScriptService.ScriptStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/scripts")
public class ScriptController {

    private final ScriptService scriptService;

    public ScriptController(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    @PostMapping
    public ResponseEntity<CreateScriptResponse> createScript(
            @Valid @RequestBody CreateScriptRequest request,
            Authentication authentication) {
        var userId = UUID.fromString(authentication.getName());
        var script = scriptService.createScript(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateScriptResponse(script.getId()));
    }

    @GetMapping
    public ResponseEntity<ListScriptsResponse> listScripts(
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var scriptStatus = ScriptStatus.valueOf(status.toUpperCase());
        var pageable = PageRequest.of(page, size);
        var scripts = scriptService.listScripts(scriptStatus, pageable);
        var response = new ListScriptsResponse(
                scripts.getContent().stream()
                        .map(ScriptResponse::from)
                        .toList(),
                scripts.getTotalElements()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScriptResponse> getScript(@PathVariable UUID id) {
        var script = scriptService.getScriptById(id);
        return ResponseEntity.ok(ScriptResponse.from(script));
    }
}
