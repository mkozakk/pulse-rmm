package dev.pulsermm.agentupdate.api.controller;

import dev.pulsermm.agentupdate.api.dto.*;
import dev.pulsermm.agentupdate.application.AgentVersionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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
            @RequestParam("arch") String arch,
            @RequestParam("artifactType") String artifactType) {

        var v = service.publish(file, version, os, arch, artifactType);
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

    @GetMapping("/api/agent-versions/checksum")
    public ResponseEntity<String> checksum(
            @RequestParam String os,
            @RequestParam String arch,
            @RequestParam String artifactType) {
        return service.list().stream()
                .filter(v -> v.isCurrent() && v.getOs().equals(os)
                        && v.getArch().equals(arch) && v.getArtifactType().equals(artifactType))
                .findFirst()
                .map(v -> ResponseEntity.ok(v.getSha256()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/install/pulse-agent-linux-amd64.tar.gz")
    public ResponseEntity<StreamingResponseBody> downloadTarGz() {
        return installProxy("linux", "amd64", "tar.gz");
    }

    @GetMapping("/install/pulse-agent-windows-amd64.zip")
    public ResponseEntity<StreamingResponseBody> downloadZip() {
        return installProxy("windows", "amd64", "zip");
    }

    @GetMapping("/install/pulse-agent.deb")
    public ResponseEntity<StreamingResponseBody> downloadDeb() {
        return installProxy("linux", "amd64", "deb");
    }

    @GetMapping("/install/pulse-agent.rpm")
    public ResponseEntity<StreamingResponseBody> downloadRpm() {
        return installProxy("linux", "amd64", "rpm");
    }

    @GetMapping("/install/pulse-agent-installer.exe")
    public ResponseEntity<StreamingResponseBody> downloadExe() {
        return installProxy("windows", "amd64", "exe");
    }

    private ResponseEntity<StreamingResponseBody> installProxy(String os, String arch, String artifactType) {
        return service.getInstallStream(os, arch, artifactType)
                .map(stream -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .<StreamingResponseBody>body(out -> {
                            try (stream) {
                                stream.transferTo(out);
                            }
                        }))
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
