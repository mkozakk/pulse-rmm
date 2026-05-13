package dev.pulsermm.alert.application;

import dev.pulsermm.alert.domain.AlertEvent;
import org.springframework.context.ApplicationEvent;

public class AlertFiredEvent extends ApplicationEvent {

    private final AlertEvent alertEvent;

    public AlertFiredEvent(Object source, AlertEvent alertEvent) {
        super(source);
        this.alertEvent = alertEvent;
    }

    public AlertEvent getAlertEvent() {
        return alertEvent;
    }
}
