package dev.pulsermm.alert.application;

import dev.pulsermm.alert.domain.AlertEvent;
import dev.pulsermm.alert.infrastructure.persistence.AlertEventRepository;
import dev.pulsermm.common.audit.Auditable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AlertEventService {

    private final AlertEventRepository eventRepository;

    public AlertEventService(AlertEventRepository eventRepository) {
        this.eventRepository = eventRepository;
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
            }
        });
    }
}
