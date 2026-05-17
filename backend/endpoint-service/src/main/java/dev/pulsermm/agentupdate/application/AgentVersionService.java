package dev.pulsermm.agentupdate.application;

import dev.pulsermm.agentupdate.domain.AgentUpdateEvent;
import dev.pulsermm.agentupdate.domain.AgentVersion;
import dev.pulsermm.agentupdate.infrastructure.persistence.AgentUpdateEventRepository;
import dev.pulsermm.agentupdate.infrastructure.persistence.AgentVersionRepository;
import dev.pulsermm.agentupdate.infrastructure.storage.MinioStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentVersionService {

    private final AgentVersionRepository repo;
    private final AgentUpdateEventRepository eventRepo;
    private final MinioStorageService storage;
    private final String apiUrl;

    public AgentVersionService(AgentVersionRepository repo,
                               AgentUpdateEventRepository eventRepo,
                               MinioStorageService storage,
                               @Value("${pulse.api.url:http://localhost:8080}") String apiUrl) {
        this.repo = repo;
        this.eventRepo = eventRepo;
        this.storage = storage;
        this.apiUrl = apiUrl;
    }

    @Transactional
    public AgentVersion publish(MultipartFile file, String version, String os, String arch, String artifactType) {
        if (repo.existsByVersionAndOsAndArchAndArtifactType(version, os, arch, artifactType)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Version " + version + " (" + artifactType + ") for " + os + "/" + arch + " already exists");
        }
        String ext = artifactExtension(os, artifactType);
        String objectKey = "agents/" + os + "/" + arch + "/" + artifactType + "/" + version + "/pulse-agent" + ext;
        MinioStorageService.UploadResult result;
        try {
            result = storage.upload(objectKey, file.getInputStream(), file.getSize(), "application/octet-stream");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload failed: " + e.getMessage());
        }
        AgentVersion v = new AgentVersion();
        v.setVersion(version);
        v.setOs(os);
        v.setArch(arch);
        v.setArtifactType(artifactType);
        v.setArtifactKey(objectKey);
        v.setSha256(result.sha256());
        v.setSizeBytes(result.sizeBytes());
        v.setCurrent(false);
        return repo.save(v);
    }

    public List<AgentVersion> list() {
        return repo.findAllByOrderByPublishedAtDesc();
    }

    @Transactional
    public AgentVersion setCurrent(UUID id) {
        AgentVersion v = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));
        repo.clearCurrentForPlatform(v.getOs(), v.getArch(), v.getArtifactType());
        v.setCurrent(true);
        return repo.save(v);
    }

    @Transactional
    public void delete(UUID id) {
        AgentVersion v = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));
        if (v.isCurrent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete the current version; set another version as current first");
        }
        repo.delete(v);
    }

    public Optional<UpdateInfo> checkUpdate(String os, String arch, String currentVersion) {
        String artifactType = "windows".equals(os) ? "zip" : "tar.gz";
        String downloadPath = "windows".equals(os)
                ? "/install/pulse-agent-windows-amd64.zip"
                : "/install/pulse-agent-linux-amd64.tar.gz";
        return repo.findByOsAndArchAndArtifactTypeAndCurrentTrue(os, arch, artifactType)
                .filter(v -> !v.getVersion().equals(currentVersion))
                .map(v -> new UpdateInfo(v.getVersion(), apiUrl + downloadPath, v.getSha256(), v.getSizeBytes()));
    }

    public Optional<InputStream> getInstallStream(String os, String arch, String artifactType) {
        return repo.findByOsAndArchAndArtifactTypeAndCurrentTrue(os, arch, artifactType)
                .map(v -> storage.download(v.getArtifactKey()));
    }

    public void recordReport(UUID endpointId, String version, String status, String reason) {
        AgentUpdateEvent e = new AgentUpdateEvent();
        e.setEndpointId(endpointId);
        e.setVersion(version);
        e.setStatus(status);
        e.setReason(reason);
        eventRepo.save(e);
    }

    private static String artifactExtension(String os, String artifactType) {
        return switch (artifactType) {
            case "deb" -> ".deb";
            case "rpm" -> ".rpm";
            case "exe" -> ".exe";
            case "zip" -> ".zip";
            default -> "windows".equals(os) ? ".zip" : ".tar.gz";
        };
    }

    public record UpdateInfo(String version, String downloadUrl, String sha256, long sizeBytes) {}
}
