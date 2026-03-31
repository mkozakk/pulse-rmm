package dev.pulsermm.rbac.infrastructure;

import dev.pulsermm.rbac.domain.EndpointGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EndpointGroupRepository extends JpaRepository<EndpointGroup, UUID> {
    Optional<EndpointGroup> findByName(String name);
}
