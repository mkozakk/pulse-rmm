package dev.pulsermm.gateway.api.internal;

import dev.pulsermm.gateway.infrastructure.grpc.PendingCommandRegistry;
import dev.pulsermm.gateway.infrastructure.grpc.ScriptCommandDispatcher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/script-commands")
public class ScriptCommandInternalController {

    private final ScriptCommandDispatcher dispatcher;
    private final PendingCommandRegistry pendingRegistry;

    public ScriptCommandInternalController(ScriptCommandDispatcher dispatcher, PendingCommandRegistry pendingRegistry) {
        this.dispatcher = dispatcher;
        this.pendingRegistry = pendingRegistry;
    }

    @PostMapping("/dispatch")
    public ResponseEntity<Void> dispatch(@RequestBody DispatchRequest request) {
        pendingRegistry.register(request.commandId(), request.callbackUrl());
        dispatcher.dispatch(
            request.endpointId(),
            request.commandId(),
            request.scriptBody(),
            request.envVars()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    public record DispatchRequest(
        UUID endpointId,
        String commandId,
        String scriptBody,
        Map<String, String> envVars,
        String callbackUrl
    ) {}
}
