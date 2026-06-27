package dev.pulsermm.agenthub.api.internal;

import dev.pulsermm.agenthub.infrastructure.grpc.PendingCommandRegistry;
import dev.pulsermm.agenthub.infrastructure.grpc.ProcessCommandDispatcher;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Hidden
@RestController
@RequestMapping("/internal/process-commands")
public class ProcessCommandInternalController {

    private final ProcessCommandDispatcher dispatcher;
    private final PendingCommandRegistry pendingRegistry;
    private final String internalSecret;

    public ProcessCommandInternalController(ProcessCommandDispatcher dispatcher,
                                            PendingCommandRegistry pendingRegistry,
                                            @Value("${pulse.identity.internal-secret}") String internalSecret) {
        this.dispatcher = dispatcher;
        this.pendingRegistry = pendingRegistry;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/list/dispatch")
    public ResponseEntity<Void> dispatchList(@RequestBody DispatchRequest request,
                                             @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!internalSecret.equals(token)) {
            return ResponseEntity.status(403).build();
        }
        pendingRegistry.register(request.commandId(), request.callbackUrl());
        dispatcher.dispatchList(request.endpointId(), request.commandId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/kill/dispatch")
    public ResponseEntity<Void> dispatchKill(@RequestBody DispatchRequest request,
                                             @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!internalSecret.equals(token)) {
            return ResponseEntity.status(403).build();
        }
        pendingRegistry.register(request.commandId(), request.callbackUrl());
        dispatcher.dispatchKill(request.endpointId(), request.commandId(), request.pid());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    public record DispatchRequest(
        UUID endpointId,
        String commandId,
        String callbackUrl,
        int pid
    ) {}
}
