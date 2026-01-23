package dev.pulsermm.gateway.infrastructure.grpc;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentRegistryTest {

    private AgentRegistry registry;

    @BeforeEach
    void setup() {
        registry = new AgentRegistry();
    }

    @Test
    void registerAndGet() {
        UUID id = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        StreamObserver<dev.pulsermm.proto.v1.GatewayCommand> obs = mock(StreamObserver.class);

        registry.register(id, obs);

        assertThat(registry.get(id)).contains(obs);
    }

    @Test
    void unregisterRemovesEntry() {
        UUID id = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        StreamObserver<dev.pulsermm.proto.v1.GatewayCommand> obs = mock(StreamObserver.class);

        registry.register(id, obs);
        registry.unregister(id);

        assertThat(registry.get(id)).isEmpty();
    }

    @Test
    void reregisterEvictsOldObserver() {
        UUID id = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        StreamObserver<dev.pulsermm.proto.v1.GatewayCommand> old = mock(StreamObserver.class);
        @SuppressWarnings("unchecked")
        StreamObserver<dev.pulsermm.proto.v1.GatewayCommand> fresh = mock(StreamObserver.class);

        registry.register(id, old);
        registry.register(id, fresh);

        verify(old).onCompleted();
        assertThat(registry.get(id)).contains(fresh);
    }

    @Test
    void getMissingReturnsEmpty() {
        assertThat(registry.get(UUID.randomUUID())).isEmpty();
    }
}
