package dev.pulsermm.alert.infrastructure.messaging;

import dev.pulsermm.alert.application.NotificationPayload;
import dev.pulsermm.alert.application.SseBroadcaster;
import dev.pulsermm.common.events.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DomainEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DomainEventConsumer.class);

    private final SseBroadcaster broadcaster;

    public DomainEventConsumer(SseBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @RabbitListener(queues = "alert.service.notifications")
    public void onEvent(DomainEvent event) {
        String message = toMessage(event);
        log.debug("Received domain event type={}, broadcasting notification", event.type());
        broadcaster.broadcastNotification(new NotificationPayload(event.type(), message, event.occurredAt()));
    }

    private String toMessage(DomainEvent event) {
        return switch (event.type()) {
            case "endpoint.enrolled" -> "Endpoint enrolled: " + event.data().getOrDefault("hostname", "unknown");
            case "endpoint.online" -> "Endpoint came online: " + event.data().getOrDefault("endpointId", "unknown");
            case "endpoint.offline" -> "Endpoint went offline: " + event.data().getOrDefault("endpointId", "unknown");
            case "script.result" -> {
                var exitCode = event.data().getOrDefault("exitCode", "?");
                var endpointId = event.data().getOrDefault("endpointId", "unknown");
                yield "Script result on " + endpointId + ": exit code " + exitCode;
            }
            case "software.command.completed" -> {
                var action = event.data().getOrDefault("action", "command");
                var exitCode = event.data().getOrDefault("exitCode", "?");
                var endpointId = event.data().getOrDefault("endpointId", "unknown");
                yield "Software " + action + " on " + endpointId + ": exit code " + exitCode;
            }
            default -> {
                // audit.* events: action is the suffix
                String action = event.data().getOrDefault("action", event.type()).toString();
                yield "Audit: " + action;
            }
        };
    }
}
