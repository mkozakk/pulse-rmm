package dev.pulsermm.identity.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "endpoint_group_memberships")
public class EndpointGroupMembership {

    @Id
    @Column(name = "endpoint_id")
    private UUID endpointId;

    @Column(name = "group_id")
    private UUID groupId;

    public EndpointGroupMembership() {}

    public EndpointGroupMembership(UUID endpointId, UUID groupId) {
        this.endpointId = endpointId;
        this.groupId = groupId;
    }

    public UUID getEndpointId() { return endpointId; }
    public UUID getGroupId() { return groupId; }
}
