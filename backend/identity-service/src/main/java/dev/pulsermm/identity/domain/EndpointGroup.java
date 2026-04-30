package dev.pulsermm.identity.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "endpoint_groups")
public class EndpointGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    public EndpointGroup() {}

    public EndpointGroup(String name) {
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
}
