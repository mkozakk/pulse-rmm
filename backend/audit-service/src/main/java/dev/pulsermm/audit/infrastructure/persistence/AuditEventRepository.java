package dev.pulsermm.audit.infrastructure.persistence;

import dev.pulsermm.audit.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    @Query("""
        SELECT a FROM AuditEvent a
        WHERE (:userId IS NULL OR a.userId = :userId)
          AND (:endpointId IS NULL OR a.endpointId = :endpointId)
          AND (:from IS NULL OR a.createdAt >= :from)
          AND (:to IS NULL OR a.createdAt <= :to)
        ORDER BY a.createdAt DESC
    """)
    Page<AuditEvent> findFiltered(UUID userId, UUID endpointId, Instant from, Instant to, Pageable pageable);
}
