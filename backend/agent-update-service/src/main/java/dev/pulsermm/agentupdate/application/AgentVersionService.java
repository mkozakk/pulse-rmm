package dev.pulsermm.agentupdate.application;

import dev.pulsermm.agentupdate.domain.AgentUpdateEvent;
import dev.pulsermm.agentupdate.domain.AgentVersion;
import dev.pulsermm.agentupdate.infrastructure.persistence.AgentUpdateEventRepository;
import dev.pulsermm.agentupdate.infrastructure.persistence.AgentVersionRepository;
import dev.pulsermm.agentupdate.infrastructure.storage.MinioStorageService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;

@Service
public class AgentVersionService {

    private final AgentVersionRepository repo;
    private final AgentUpdateEventRepository eventRepo;
    private final MinioStorageService storage;

    public AgentVersionService(AgentVersionRepository repo,
                               AgentUpdateEventRepository eventRepo,
                               MinioStorageService storage) {
        this.repo = repo;
        this.eventRepo = eventRepo;
        this.storage = storage;
    }

    @Transactional
    public AgentVersion publish(MultipartFile file, String version, String os, String arch) {
        if (repo.existsByVersionAndOsAndArch(version, os, arch)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Version " + version + " for " + os + "/" + arch + " already exists");
        }

        String filename = "pulse-agent" + ("windows".equals(os) ? ".exe" : "");
        String objectKey = "agents/" + os + "/" + arch + "/" + version + "/" + filename;

        MinioStorageService.UploadResult result;
        try {
            result = storage.upload(objectKey, file.getInputStream(), file.getSize(),
                    "application/octet-stream");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Upload failed: " + e.getMessage());
        }

        AgentVersion v = new AgentVersion();
        v.setVersion(version);
        v.setOs(os);
        v.setArch(arch);
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

        repo.clearCurrentForPlatform(v.getOs(), v.getArch());
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
        return repo.findByOsAndArchAndCurrentTrue(os, arch)
                .filter(v -> !v.getVersion().equals(currentVersion))
                .map(v -> {
                    String url = storage.presignDownloadUrl(v.getArtifactKey());
                    return new UpdateInfo(v.getVersion(), url, v.getSha256(), v.getSizeBytes());
                });
    }

    public void recordReport(UUID endpointId, String version, String status, String reason) {
        AgentUpdateEvent e = new AgentUpdateEvent();
        e.setEndpointId(endpointId);
        e.setVersion(version);
        e.setStatus(status);
        e.setReason(reason);
        eventRepo.save(e);
    }

    public record UpdateInfo(String version, String downloadUrl, String sha256, long sizeBytes) {}
}
