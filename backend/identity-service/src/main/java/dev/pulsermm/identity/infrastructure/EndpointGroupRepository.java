package dev.pulsermm.identity.infrastructure;

import dev.pulsermm.identity.domain.EndpointGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EndpointGroupRepository extends JpaRepository<EndpointGroup, UUID> {
    Optional<EndpointGroup> findByName(String name);
}
