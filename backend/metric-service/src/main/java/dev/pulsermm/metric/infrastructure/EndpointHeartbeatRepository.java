package dev.pulsermm.metric.infrastructure;

import dev.pulsermm.metric.domain.EndpointHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface EndpointHeartbeatRepository extends JpaRepository<EndpointHeartbeat, UUID> {

    @Modifying
    @Query("UPDATE EndpointHeartbeat h SET h.status = 'offline' WHERE h.lastSeen < :cutoff AND h.status = 'online'")
    void markOfflineWhere(@Param("cutoff") Instant cutoff);
}
