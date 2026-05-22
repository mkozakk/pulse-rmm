package dev.pulsermm.enrolment.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "groups", schema = "enrolment")
public class Group {
    @Id
    private UUID id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "parent_id")
    private UUID parentId;

    public Group() {}

    public Group(UUID id, String name, UUID parentId) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }
}
