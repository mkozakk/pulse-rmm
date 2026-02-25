package dev.pulsermm.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditPublisher.class);
    private static final String EXCHANGE = "audit.events";

    private final RabbitTemplate rabbitTemplate;

    public AuditPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(AuditEventMessage message) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, "", message);
        } catch (Exception e) {
            log.warn("Failed to publish audit event for action {}: {}", message.action(), e.getMessage());
        }
    }
}
