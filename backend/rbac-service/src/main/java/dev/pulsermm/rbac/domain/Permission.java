package dev.pulsermm.rbac.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Schema(description = "Permission id")
    private UUID id;

    @Column(nullable = false, unique = true)
    @Schema(description = "Permission name", example = "scripts.run")
    private String name;

    public Permission() {}

    public Permission(String name) {
        this.name = name;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
}
