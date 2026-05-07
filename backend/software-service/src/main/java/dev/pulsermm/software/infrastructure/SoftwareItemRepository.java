package dev.pulsermm.software.infrastructure;

import dev.pulsermm.software.domain.SoftwareItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SoftwareItemRepository extends JpaRepository<SoftwareItem, UUID> {
    List<SoftwareItem> findByEndpointId(UUID endpointId);
    void deleteByEndpointId(UUID endpointId);
}
