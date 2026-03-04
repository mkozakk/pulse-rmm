package dev.pulsermm.common.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class DomainEventPublisher {

    public static final String EXCHANGE = "pulse.events";

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public DomainEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(DomainEvent event) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, event.type(), event);
        } catch (Exception e) {
            log.warn("Failed to publish domain event type={}: {}", event.type(), e.getMessage());
        }
    }
}
