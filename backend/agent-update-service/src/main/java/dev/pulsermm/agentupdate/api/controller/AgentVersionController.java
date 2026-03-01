package dev.pulsermm.agentupdate.api.controller;

import dev.pulsermm.agentupdate.api.dto.AgentVersionResponse;
import dev.pulsermm.agentupdate.api.dto.UpdateCheckResponse;
import dev.pulsermm.agentupdate.api.dto.UpdateReportRequest;
import dev.pulsermm.agentupdate.application.AgentVersionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class AgentVersionController {

    private final AgentVersionService service;

    public AgentVersionController(AgentVersionService service) {
        this.service = service;
    }

    @PostMapping("/api/updates/report")
    public ResponseEntity<Void> report(@RequestBody @Valid UpdateReportRequest req) {
        service.recordReport(req.endpointId(), req.version(), req.status(), req.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/agent-versions")
    public ResponseEntity<AgentVersionResponse> publish(
            @RequestParam("file") MultipartFile file,
            @RequestParam("version") String version,
            @RequestParam("os") String os,
            @RequestParam("arch") String arch) {

        var v = service.publish(file, version, os, arch);
        return ResponseEntity
                .created(URI.create("/api/agent-versions/" + v.getId()))
                .body(AgentVersionResponse.from(v));
    }

    @GetMapping("/api/agent-versions")
    public List<AgentVersionResponse> list() {
        return service.list().stream().map(AgentVersionResponse::from).toList();
    }

    @PutMapping("/api/agent-versions/{id}/current")
    public AgentVersionResponse setCurrent(@PathVariable UUID id) {
        return AgentVersionResponse.from(service.setCurrent(id));
    }

    @DeleteMapping("/api/agent-versions/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/install/pulse-agent.deb")
    public ResponseEntity<Void> downloadDeb() {
        return packageRedirect("linux", "amd64");
    }

    @GetMapping("/install/pulse-agent.rpm")
    public ResponseEntity<Void> downloadRpm() {
        return packageRedirect("linux", "amd64");
    }

    private ResponseEntity<Void> packageRedirect(String os, String arch) {
        return service.checkUpdate(os, arch, "")
                .map(info -> ResponseEntity.status(302)
                        .header("Location", info.downloadUrl())
                        .<Void>build())
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/updates/check")
    public UpdateCheckResponse checkUpdate(
            @RequestParam String os,
            @RequestParam String arch,
            @RequestParam String version) {

        return service.checkUpdate(os, arch, version)
                .map(info -> UpdateCheckResponse.available(
                        info.version(), info.downloadUrl(), info.sha256(), info.sizeBytes()))
                .orElse(UpdateCheckResponse.current());
    }
}
