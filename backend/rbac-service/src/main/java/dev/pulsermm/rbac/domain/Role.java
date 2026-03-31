package dev.pulsermm.rbac.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Schema(description = "Role id")
    private UUID id;

    @Column(nullable = false, unique = true)
    @Schema(description = "Role name", example = "Admin")
    private String name;

    public Role() {}

    public Role(String name) {
        this.name = name;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
}
