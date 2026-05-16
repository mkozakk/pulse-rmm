package dev.pulsermm.agentupdate.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_versions", schema = "agent_update")
public class AgentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String version;

    @Column(nullable = false, length = 20)
    private String os;

    @Column(nullable = false, length = 20)
    private String arch;

    @Column(name = "artifact_type", nullable = false, length = 20)
    private String artifactType;

    @Column(name = "artifact_key", nullable = false, length = 200)
    private String artifactKey;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt = Instant.now();

    public UUID getId() { return id; }
    public String getVersion() { return version; }
    public String getOs() { return os; }
    public String getArch() { return arch; }
    public String getArtifactType() { return artifactType; }
    public String getArtifactKey() { return artifactKey; }
    public String getSha256() { return sha256; }
    public long getSizeBytes() { return sizeBytes; }
    public boolean isCurrent() { return current; }
    public Instant getPublishedAt() { return publishedAt; }

    public void setVersion(String version) { this.version = version; }
    public void setOs(String os) { this.os = os; }
    public void setArch(String arch) { this.arch = arch; }
    public void setArtifactType(String artifactType) { this.artifactType = artifactType; }
    public void setArtifactKey(String artifactKey) { this.artifactKey = artifactKey; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public void setCurrent(boolean current) { this.current = current; }
}
