package dev.pulsermm.audit.infrastructure.messaging;

import dev.pulsermm.audit.domain.AuditEvent;
import dev.pulsermm.audit.infrastructure.persistence.AuditEventRepository;
import dev.pulsermm.common.audit.AuditEventMessage;
import dev.pulsermm.common.events.DomainEvent;
import dev.pulsermm.common.events.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditEventRepository repository;
    private final DomainEventPublisher domainEventPublisher;

    public AuditEventConsumer(AuditEventRepository repository, DomainEventPublisher domainEventPublisher) {
        this.repository = repository;
        this.domainEventPublisher = domainEventPublisher;
    }

    @RabbitListener(queues = "audit.events.persist")
    public void consume(AuditEventMessage message) {
        try {
            var event = new AuditEvent(
                UUID.randomUUID(),
                message.userId(),
                message.username(),
                message.permissionUsed(),
                message.action(),
                message.endpointId(),
                message.payloadJson(),
                message.createdAt(),
                message.orgId()
            );
            repository.save(event);
            publishAuditDomainEvent(message, event.getId());
        } catch (Exception e) {
            log.error("Failed to persist audit event for action {}: {}", message.action(), e.getMessage(), e);
            throw e;
        }
    }

    private void publishAuditDomainEvent(AuditEventMessage message, UUID auditEventId) {
        Map<String, Object> data = new HashMap<>();
        data.put("auditEventId", auditEventId.toString());
        data.put("action", message.action());
        if (message.userId() != null) data.put("userId", message.userId().toString());
        if (message.endpointId() != null) data.put("endpointId", message.endpointId().toString());
        domainEventPublisher.publish(DomainEvent.of("audit." + message.action(), data));
    }
}
