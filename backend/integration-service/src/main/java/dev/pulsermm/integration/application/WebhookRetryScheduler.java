package dev.pulsermm.integration.application;

import dev.pulsermm.integration.infrastructure.persistence.WebhookDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class WebhookRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryScheduler.class);

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDispatcher dispatcher;

    public WebhookRetryScheduler(WebhookDeliveryRepository deliveryRepository, WebhookDispatcher dispatcher) {
        this.deliveryRepository = deliveryRepository;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelay = 5000)
    public void retryPending() {
        var retryable = deliveryRepository.findRetryable(Instant.now());
        if (!retryable.isEmpty()) {
            log.debug("retrying {} deliveries", retryable.size());
        }
        for (var delivery : retryable) {
            dispatcher.dispatch(delivery);
        }
    }
}
