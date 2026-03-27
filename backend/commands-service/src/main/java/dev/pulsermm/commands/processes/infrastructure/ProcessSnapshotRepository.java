package dev.pulsermm.commands.processes.infrastructure;

import dev.pulsermm.commands.processes.domain.ProcessSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessSnapshotRepository extends JpaRepository<ProcessSnapshot, UUID> {
    Optional<ProcessSnapshot> findTopByEndpointIdAndStatusOrderByCompletedAtDesc(UUID endpointId, String status);
}
