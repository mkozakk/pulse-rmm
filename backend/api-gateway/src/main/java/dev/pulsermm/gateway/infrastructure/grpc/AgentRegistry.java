package dev.pulsermm.gateway.infrastructure.grpc;

import dev.pulsermm.proto.v1.GatewayCommand;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentRegistry {

    private final Map<UUID, StreamObserver<GatewayCommand>> agents = new ConcurrentHashMap<>();

    public void register(UUID endpointId, StreamObserver<GatewayCommand> sink) {
        StreamObserver<GatewayCommand> old = agents.put(endpointId, new SynchronizedObserver(sink));
        if (old != null) {
            try {
                old.onCompleted();
            } catch (Exception ignored) {
            }
        }
    }

    public void unregister(UUID endpointId) {
        agents.remove(endpointId);
    }

    public Optional<StreamObserver<GatewayCommand>> get(UUID endpointId) {
        return Optional.ofNullable(agents.get(endpointId));
    }

    // grpc-java StreamObserver is not thread-safe; multiple WebSocket/REST threads
    // call onNext() concurrently for the same agent stream, corrupting MessageFramer.
    private static final class SynchronizedObserver implements StreamObserver<GatewayCommand> {
        private final StreamObserver<GatewayCommand> delegate;

        SynchronizedObserver(StreamObserver<GatewayCommand> delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized void onNext(GatewayCommand value) {
            delegate.onNext(value);
        }

        @Override
        public synchronized void onError(Throwable t) {
            delegate.onError(t);
        }

        @Override
        public synchronized void onCompleted() {
            delegate.onCompleted();
        }
    }
}
