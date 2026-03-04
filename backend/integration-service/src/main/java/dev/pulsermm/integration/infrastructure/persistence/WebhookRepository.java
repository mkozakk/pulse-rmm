package dev.pulsermm.integration.infrastructure.persistence;

import dev.pulsermm.integration.domain.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WebhookRepository extends JpaRepository<Webhook, UUID> {

    List<Webhook> findAllByEnabledTrue();

    @Query(value = "SELECT * FROM integration.webhooks WHERE enabled = true AND :eventType = ANY(event_types)", nativeQuery = true)
    List<Webhook> findByEventType(@Param("eventType") String eventType);
}
