package dev.pulsermm.agentupdate.api.controller;

import dev.pulsermm.agentupdate.api.dto.*;
import dev.pulsermm.agentupdate.application.AgentVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Agent Versions", description = "Manage agent binary releases and serve update checks to enrolled agents")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class AgentVersionController {

    private final AgentVersionService service;

    public AgentVersionController(AgentVersionService service) {
        this.service = service;
    }

    @Operation(summary = "Report update result", description = "Called by the agent after applying (or failing to apply) an update. No auth required — agent uses mTLS.")
    @ApiResponse(responseCode = "204", description = "Report recorded")
    @PostMapping("/api/updates/report")
    public ResponseEntity<Void> report(@RequestBody @Valid UpdateReportRequest req) {
        service.recordReport(req.endpointId(), req.version(), req.status(), req.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Publish a new agent version", description = "Uploads an agent binary and registers it as a new release. Multipart form — file + version metadata.")
    @ApiResponse(responseCode = "201", description = "Version published")
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

    @Operation(summary = "List all agent versions")
    @ApiResponse(responseCode = "200", description = "Versions returned")
    @GetMapping("/api/agent-versions")
    public List<AgentVersionResponse> list() {
        return service.list().stream().map(AgentVersionResponse::from).toList();
    }

    @Operation(summary = "Set a version as current", description = "Marks this version as the one agents should upgrade to. Clears the current flag from any previous version.")
    @ApiResponse(responseCode = "200", description = "Version set as current")
    @ApiResponse(responseCode = "404", description = "Version not found")
    @PutMapping("/api/agent-versions/{id}/current")
    public AgentVersionResponse setCurrent(@PathVariable UUID id) {
        return AgentVersionResponse.from(service.setCurrent(id));
    }

    @Operation(summary = "Delete an agent version")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "409", description = "Cannot delete the current version")
    @DeleteMapping("/api/agent-versions/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get checksum for current version", description = "Returns the SHA-256 checksum for the current agent binary matching the given os/arch/artifactType.")
    @ApiResponse(responseCode = "200", description = "Checksum returned")
    @ApiResponse(responseCode = "404", description = "No current version for the given platform")
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

    @Operation(summary = "Download agent — Linux tar.gz", description = "Proxies the current agent binary for linux/amd64 as a tar.gz.")
    @ApiResponse(responseCode = "200", description = "Binary stream")
    @ApiResponse(responseCode = "404", description = "No current version published")
    @GetMapping("/install/pulse-agent-linux-amd64.tar.gz")
    public ResponseEntity<StreamingResponseBody> downloadTarGz() {
        return installProxy("linux", "amd64", "tar.gz");
    }

    @Operation(summary = "Download agent — Windows zip")
    @ApiResponse(responseCode = "200", description = "Binary stream")
    @ApiResponse(responseCode = "404", description = "No current version published")
    @GetMapping("/install/pulse-agent-windows-amd64.zip")
    public ResponseEntity<StreamingResponseBody> downloadZip() {
        return installProxy("windows", "amd64", "zip");
    }

    @Operation(summary = "Download agent — Linux deb")
    @ApiResponse(responseCode = "200", description = "Binary stream")
    @ApiResponse(responseCode = "404", description = "No current version published")
    @GetMapping("/install/pulse-agent.deb")
    public ResponseEntity<StreamingResponseBody> downloadDeb() {
        return installProxy("linux", "amd64", "deb");
    }

    @Operation(summary = "Download agent — Linux rpm")
    @ApiResponse(responseCode = "200", description = "Binary stream")
    @ApiResponse(responseCode = "404", description = "No current version published")
    @GetMapping("/install/pulse-agent.rpm")
    public ResponseEntity<StreamingResponseBody> downloadRpm() {
        return installProxy("linux", "amd64", "rpm");
    }

    @Operation(summary = "Download agent — Windows exe installer")
    @ApiResponse(responseCode = "200", description = "Binary stream")
    @ApiResponse(responseCode = "404", description = "No current version published")
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

    @Operation(summary = "Check for agent update", description = "Called by the agent on startup and periodically. Returns whether a newer version is available for the given platform.")
    @ApiResponse(responseCode = "200", description = "Update check result returned")
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
