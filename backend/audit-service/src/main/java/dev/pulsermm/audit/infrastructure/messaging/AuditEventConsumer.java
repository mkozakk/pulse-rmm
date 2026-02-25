package dev.pulsermm.audit.infrastructure.messaging;

import dev.pulsermm.audit.domain.AuditEvent;
import dev.pulsermm.audit.infrastructure.persistence.AuditEventRepository;
import dev.pulsermm.common.audit.AuditEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditEventRepository repository;

    public AuditEventConsumer(AuditEventRepository repository) {
        this.repository = repository;
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
                message.createdAt()
            );
            repository.save(event);
        } catch (Exception e) {
            log.error("Failed to persist audit event for action {}: {}", message.action(), e.getMessage(), e);
            throw e;
        }
    }
}
