package dev.pulsermm.integration.infrastructure.persistence;

import dev.pulsermm.integration.domain.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Page<WebhookDelivery> findByWebhookIdOrderByCreatedAtDesc(UUID webhookId, Pageable pageable);

    Page<WebhookDelivery> findByWebhookIdAndStatusOrderByCreatedAtDesc(UUID webhookId, String status, Pageable pageable);

    Page<WebhookDelivery> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    @Query("SELECT d FROM WebhookDelivery d WHERE d.status IN ('pending', 'retrying') AND d.nextRetryAt <= :now")
    List<WebhookDelivery> findRetryable(Instant now);
}
