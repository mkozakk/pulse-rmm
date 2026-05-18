package dev.pulsermm.commands.software.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "software_items", schema = "software", uniqueConstraints = {
    @UniqueConstraint(name = "uk_software_items_endpoint_app_id", columnNames = {"endpoint_id", "app_id"})
})
public class SoftwareItem {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID endpointId;

    @Column(nullable = false)
    private String name;

    @Column(name = "app_id")
    private String appId;

    @Column(nullable = false)
    private String version;

    @Column(name = "update_to")
    private String updateTo;

    @Column(name = "is_store")
    private Boolean isStore;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private LocalDateTime lastScannedAt;

    protected SoftwareItem() {}

    public SoftwareItem(UUID id, UUID endpointId, String name, String appId, String version, String updateTo, Boolean isStore, String source, LocalDateTime lastScannedAt) {
        this.id = id;
        this.endpointId = endpointId;
        this.name = name;
        this.appId = appId;
        this.version = version;
        this.updateTo = updateTo;
        this.isStore = isStore;
        this.source = source;
        this.lastScannedAt = lastScannedAt;
    }

    public UUID id() { return id; }
    public UUID endpointId() { return endpointId; }
    public String name() { return name; }
    public String appId() { return appId; }
    public String version() { return version; }
    public String updateTo() { return updateTo; }
    public Boolean isStore() { return isStore; }
    public String source() { return source; }
    public LocalDateTime lastScannedAt() { return lastScannedAt; }
}
