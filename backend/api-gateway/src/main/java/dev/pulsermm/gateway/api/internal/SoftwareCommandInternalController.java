package dev.pulsermm.gateway.api.internal;

import dev.pulsermm.gateway.infrastructure.grpc.SoftwareCommandDispatcher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
            request.version()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    public record DispatchRequest(UUID endpointId, String commandId, String action, String name, String version) {}
}
