package dev.pulsermm.identity.infrastructure;

import dev.pulsermm.identity.domain.EndpointGroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EndpointGroupMembershipRepository extends JpaRepository<EndpointGroupMembership, UUID> {
    Optional<EndpointGroupMembership> findByEndpointId(UUID endpointId);
}
