package dev.pulsermm.metric.infrastructure;

import dev.pulsermm.metric.domain.EndpointHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EndpointHeartbeatRepository extends JpaRepository<EndpointHeartbeat, UUID> {

    @Query("SELECT h.endpointId FROM EndpointHeartbeat h WHERE h.lastSeen < :cutoff AND h.status = 'online'")
    List<UUID> findOnlineWithLastSeenBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("UPDATE EndpointHeartbeat h SET h.status = 'offline' WHERE h.lastSeen < :cutoff AND h.status = 'online'")
    void markOfflineWhere(@Param("cutoff") Instant cutoff);
}
