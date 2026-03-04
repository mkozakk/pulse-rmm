package dev.pulsermm.alert.application;

import dev.pulsermm.alert.domain.AlertEvent;
import dev.pulsermm.alert.infrastructure.persistence.AlertEventRepository;
import dev.pulsermm.common.audit.Auditable;
import dev.pulsermm.common.events.DomainEvent;
import dev.pulsermm.common.events.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AlertEventService {

    private final AlertEventRepository eventRepository;
    private final DomainEventPublisher domainEventPublisher;

    public AlertEventService(AlertEventRepository eventRepository, DomainEventPublisher domainEventPublisher) {
        this.eventRepository = eventRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    public List<AlertEvent> listOpen() {
        return eventRepository.findAllOpen();
    }

    public List<AlertEvent> listAll() {
        return eventRepository.findAllOrderByTriggeredAtDesc();
    }

    @Auditable(action = "alert.ack", permission = "alert:manage")
    @Transactional
    public void ack(UUID eventId, UUID userId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            if (event.isOpen()) {
                event.ack(userId);
                eventRepository.save(event);
                domainEventPublisher.publish(DomainEvent.of("alert.acknowledged", Map.of(
                    "alertEventId", eventId.toString(),
                    "ackedBy", userId.toString()
                )));
            }
        });
    }
}
