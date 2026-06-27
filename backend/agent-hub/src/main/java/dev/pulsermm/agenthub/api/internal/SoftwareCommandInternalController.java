package dev.pulsermm.agenthub.api.internal;

import dev.pulsermm.agenthub.infrastructure.grpc.SoftwareCommandDispatcher;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Hidden
@RestController
@RequestMapping("/internal/software-commands")
public class SoftwareCommandInternalController {

    private final SoftwareCommandDispatcher dispatcher;
    private final String internalSecret;

    public SoftwareCommandInternalController(SoftwareCommandDispatcher dispatcher,
                                             @Value("${pulse.identity.internal-secret}") String internalSecret) {
        this.dispatcher = dispatcher;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/dispatch")
    public ResponseEntity<Void> dispatch(@RequestBody DispatchRequest request,
                                         @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!internalSecret.equals(token)) {
            return ResponseEntity.status(403).build();
        }
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
