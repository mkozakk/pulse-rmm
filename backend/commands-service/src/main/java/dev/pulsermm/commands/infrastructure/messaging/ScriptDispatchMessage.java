package dev.pulsermm.commands.infrastructure.messaging;

import java.util.Map;
import java.util.UUID;

public record ScriptDispatchMessage(
        UUID endpointId,
        UUID commandId,
        String scriptBody,
        Map<String, String> envVars,
        String callbackUrl
) {}
