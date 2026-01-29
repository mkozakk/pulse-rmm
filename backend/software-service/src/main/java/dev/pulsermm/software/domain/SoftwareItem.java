package dev.pulsermm.software.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "software_items", uniqueConstraints = {
    @UniqueConstraint(name = "uk_software_items_endpoint_name", columnNames = {"endpoint_id", "name"})
})
public class SoftwareItem {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID endpointId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private LocalDateTime lastScannedAt;

    protected SoftwareItem() {}

    public SoftwareItem(UUID id, UUID endpointId, String name, String version, String source, LocalDateTime lastScannedAt) {
        this.id = id;
        this.endpointId = endpointId;
        this.name = name;
        this.version = version;
        this.source = source;
        this.lastScannedAt = lastScannedAt;
    }

    public UUID id() { return id; }
    public UUID endpointId() { return endpointId; }
    public String name() { return name; }
    public String version() { return version; }
    public String source() { return source; }
    public LocalDateTime lastScannedAt() { return lastScannedAt; }
}
