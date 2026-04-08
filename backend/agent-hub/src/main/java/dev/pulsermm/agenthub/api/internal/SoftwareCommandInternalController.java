package dev.pulsermm.agenthub.api.internal;

import dev.pulsermm.agenthub.infrastructure.grpc.SoftwareCommandDispatcher;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Hidden
@RestController
@RequestMapping("/internal/software-commands")
public class SoftwareCommandInternalController {

    private final SoftwareCommandDispatcher dispatcher;

    public SoftwareCommandInternalController(SoftwareCommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping("/dispatch")
    public ResponseEntity<Void> dispatch(@RequestBody DispatchRequest request) {
        dispatcher.dispatch(
            request.endpointId(),
            request.commandId(),
            request.action(),
            request.name(),
            request.appId(),
            request.version()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    public record DispatchRequest(UUID endpointId, String commandId, String action, String name, String appId, String version) {}
}
