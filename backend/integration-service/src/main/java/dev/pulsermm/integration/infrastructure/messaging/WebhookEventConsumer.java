package dev.pulsermm.integration.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.common.events.DomainEvent;
import dev.pulsermm.integration.domain.Webhook;
import dev.pulsermm.integration.domain.WebhookDelivery;
import dev.pulsermm.integration.infrastructure.persistence.WebhookDeliveryRepository;
import dev.pulsermm.integration.infrastructure.persistence.WebhookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class WebhookEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventConsumer.class);

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;

    public WebhookEventConsumer(WebhookRepository webhookRepository,
                                 WebhookDeliveryRepository deliveryRepository,
                                 ObjectMapper objectMapper) {
        this.webhookRepository = webhookRepository;
        this.deliveryRepository = deliveryRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "webhook.dispatch")
    @Transactional
    public void onEvent(DomainEvent event) {
        List<Webhook> matches = findMatchingWebhooks(event.type());
        if (matches.isEmpty()) {
            return;
        }
        log.debug("Routing event type={} to {} webhooks", event.type(), matches.size());
        for (Webhook webhook : matches) {
            var delivery = new WebhookDelivery(webhook, event.type(), event.id(), event.data());
            deliveryRepository.save(delivery);
        }
    }

    private List<Webhook> findMatchingWebhooks(String eventType) {
        // Exact match
        List<Webhook> exact = webhookRepository.findByEventType(eventType);
        // Wildcard: audit.* matches audit.anything
        if (eventType.contains(".")) {
            String prefix = eventType.substring(0, eventType.indexOf('.'));
            List<Webhook> wildcardMatches = webhookRepository.findByEventType(prefix + ".*");
            if (!wildcardMatches.isEmpty()) {
                // Merge, avoiding duplicates
                var ids = exact.stream().map(Webhook::getId).collect(java.util.stream.Collectors.toSet());
                wildcardMatches.stream()
                    .filter(w -> !ids.contains(w.getId()))
                    .forEach(exact::add);
            }
        }
        return exact;
    }
}
