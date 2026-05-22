package dev.pulsermm.commands.software.infrastructure;

import dev.pulsermm.commands.software.domain.SoftwareCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SoftwareCommandRepository extends JpaRepository<SoftwareCommand, UUID> {
    List<SoftwareCommand> findByEndpointId(UUID endpointId);
    List<SoftwareCommand> findByEndpointIdAndStatus(UUID endpointId, String status);
}
