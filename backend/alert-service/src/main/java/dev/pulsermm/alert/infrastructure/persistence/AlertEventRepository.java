package dev.pulsermm.alert.infrastructure.persistence;

import dev.pulsermm.alert.domain.AlertEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID> {

    @Query("SELECT e FROM AlertEvent e WHERE e.ackedAt IS NULL ORDER BY e.triggeredAt DESC")
    List<AlertEvent> findAllOpen();

    @Query("SELECT e FROM AlertEvent e ORDER BY e.triggeredAt DESC")
    List<AlertEvent> findAllOrderByTriggeredAtDesc();

    // Open event for a rule+endpoint (prevents double-fire)
    @Query("SELECT e FROM AlertEvent e WHERE e.rule.id = :ruleId AND e.endpointId = :endpointId AND e.ackedAt IS NULL")
    Optional<AlertEvent> findOpenEvent(@Param("ruleId") UUID ruleId, @Param("endpointId") UUID endpointId);

    // Acked but condition not yet cleared — prevents re-fire after ack until condition resolves
    @Query("SELECT COUNT(e) > 0 FROM AlertEvent e WHERE e.rule.id = :ruleId AND e.endpointId = :endpointId AND e.ackedAt IS NOT NULL AND e.clearedAt IS NULL")
    boolean existsAckedButNotCleared(@Param("ruleId") UUID ruleId, @Param("endpointId") UUID endpointId);

    @Modifying
    @Query("UPDATE AlertEvent e SET e.clearedAt = CURRENT_TIMESTAMP WHERE e.rule.id = :ruleId AND e.endpointId = :endpointId AND e.clearedAt IS NULL")
    void markCleared(@Param("ruleId") UUID ruleId, @Param("endpointId") UUID endpointId);
}
