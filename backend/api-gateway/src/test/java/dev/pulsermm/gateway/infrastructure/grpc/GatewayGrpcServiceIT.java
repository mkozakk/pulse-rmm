package dev.pulsermm.gateway.infrastructure.grpc;

import dev.pulsermm.proto.v1.AgentEvent;
import dev.pulsermm.proto.v1.AgentHello;
import dev.pulsermm.proto.v1.GatewayCommand;
import dev.pulsermm.proto.v1.GatewayServiceGrpc;
import dev.pulsermm.proto.v1.ShellOutput;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayGrpcServiceIT {

    private AgentRegistry registry;
    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void setup() throws IOException {
        registry = new AgentRegistry();
        String serverName = InProcessServerBuilder.generateName();

        server = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(new GatewayGrpcService(registry))
            .build()
            .start();

        channel = InProcessChannelBuilder.forName(serverName)
            .directExecutor()
            .build();
    }

    @AfterEach
    void teardown() throws InterruptedException {
        channel.shutdownNow();
        server.shutdownNow();
        server.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    void helloRegistersEndpoint() throws InterruptedException {
        UUID endpointId = UUID.randomUUID();
        StreamObserver<AgentEvent> stream = GatewayServiceGrpc.newStub(channel)
            .openAgentStream(noopObserver());

        stream.onNext(AgentEvent.newBuilder()
            .setHello(AgentHello.newBuilder()
                .setEndpointId(endpointId.toString())
                .setAgentVersion("0.5.0")
                .build())
            .build());

        Thread.sleep(100);

        assertThat(registry.get(endpointId)).isPresent();

        stream.onCompleted();
    }

    @Test
    void nonHelloFirstMessageClosesWithInvalidArgument() throws InterruptedException {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch closed = new CountDownLatch(1);

        StreamObserver<AgentEvent> stream = GatewayServiceGrpc.newStub(channel)
            .openAgentStream(new StreamObserver<>() {
                public void onNext(GatewayCommand cmd) {}
                public void onError(Throwable t) { error.set(t); closed.countDown(); }
                public void onCompleted() { closed.countDown(); }
            });

        stream.onNext(AgentEvent.newBuilder()
            .setShellOutput(ShellOutput.newBuilder()
                .setSessionId("x").setData(ByteString.EMPTY).build())
            .build());

        assertThat(closed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) error.get()).getStatus().getCode())
            .isEqualTo(Status.INVALID_ARGUMENT.getCode());
    }

    @Test
    void closeStreamUnregistersEndpoint() throws InterruptedException {
        UUID endpointId = UUID.randomUUID();
        CountDownLatch closed = new CountDownLatch(1);

        StreamObserver<AgentEvent> stream = GatewayServiceGrpc.newStub(channel)
            .openAgentStream(new StreamObserver<>() {
                public void onNext(GatewayCommand cmd) {}
                public void onError(Throwable t) { closed.countDown(); }
                public void onCompleted() { closed.countDown(); }
            });

        stream.onNext(AgentEvent.newBuilder()
            .setHello(AgentHello.newBuilder().setEndpointId(endpointId.toString()).build())
            .build());
        Thread.sleep(100);

        stream.onCompleted();
        assertThat(closed.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(100);

        assertThat(registry.get(endpointId)).isEmpty();
    }

    private <T> StreamObserver<T> noopObserver() {
        return new StreamObserver<>() {
            public void onNext(T v) {}
            public void onError(Throwable t) {}
            public void onCompleted() {}
        };
    }
}
