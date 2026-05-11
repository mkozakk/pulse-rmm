package dev.pulsermm.software.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "software_commands")
public class SoftwareCommand {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID endpointId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String packageName;

    @Column(name = "app_id")
    private String appId;

    private String packageVersion;

    @Column(nullable = false)
    private String status;

    private Integer exitCode;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    protected SoftwareCommand() {}

    public SoftwareCommand(UUID id, UUID endpointId, String action, String packageName, String appId, String packageVersion) {
        this.id = id;
        this.endpointId = endpointId;
        this.action = action;
        this.packageName = packageName;
        this.appId = appId;
        this.packageVersion = packageVersion;
        this.status = "pending";
        this.createdAt = LocalDateTime.now();
    }

    public void complete(int exitCode, String output) {
        this.status = "completed";
        this.exitCode = exitCode;
        this.output = output;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String output) {
        this.status = "failed";
        this.output = output;
        this.completedAt = LocalDateTime.now();
    }

    public UUID id() { return id; }
    public UUID endpointId() { return endpointId; }
    public String action() { return action; }
    public String packageName() { return packageName; }
    public String appId() { return appId; }
    public String packageVersion() { return packageVersion; }
    public String status() { return status; }
    public Integer exitCode() { return exitCode; }
    public String output() { return output; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime completedAt() { return completedAt; }
}
