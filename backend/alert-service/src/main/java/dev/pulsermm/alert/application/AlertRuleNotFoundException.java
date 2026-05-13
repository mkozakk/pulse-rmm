package dev.pulsermm.alert.application;

import java.util.UUID;

public class AlertRuleNotFoundException extends RuntimeException {

    public AlertRuleNotFoundException(UUID id) {
        super("Alert rule not found: " + id);
    }
}
