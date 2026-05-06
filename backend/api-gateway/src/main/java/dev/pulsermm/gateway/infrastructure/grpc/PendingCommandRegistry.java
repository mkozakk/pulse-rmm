package dev.pulsermm.gateway.infrastructure.grpc;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PendingCommandRegistry {

    private final ConcurrentHashMap<String, String> pending = new ConcurrentHashMap<>();

    public void register(String commandId, String callbackUrl) {
        pending.put(commandId, callbackUrl);
    }

    public Optional<String> remove(String commandId) {
        return Optional.ofNullable(pending.remove(commandId));
    }
}
