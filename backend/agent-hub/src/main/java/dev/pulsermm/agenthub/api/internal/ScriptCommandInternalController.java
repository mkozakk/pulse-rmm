package dev.pulsermm.agenthub.api.internal;

import dev.pulsermm.agenthub.infrastructure.grpc.PendingCommandRegistry;
import dev.pulsermm.agenthub.infrastructure.grpc.ScriptCommandDispatcher;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Hidden
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
