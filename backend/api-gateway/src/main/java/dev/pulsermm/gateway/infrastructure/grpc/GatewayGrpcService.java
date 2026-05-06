package dev.pulsermm.gateway.infrastructure.grpc;

import dev.pulsermm.gateway.infrastructure.ws.ShellSessionRouter;
import dev.pulsermm.proto.v1.AgentEvent;
import dev.pulsermm.proto.v1.GatewayCommand;
import dev.pulsermm.proto.v1.GatewayServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class GatewayGrpcService extends GatewayServiceGrpc.GatewayServiceImplBase {

    private final AgentRegistry registry;
    private final ShellSessionRouter router;
    private final PendingCommandRegistry pendingCommandRegistry;

    public GatewayGrpcService(AgentRegistry registry, ShellSessionRouter router, PendingCommandRegistry pendingCommandRegistry) {
        this.registry = registry;
        this.router = router;
        this.pendingCommandRegistry = pendingCommandRegistry;
    }

    @Override
    public StreamObserver<AgentEvent> openAgentStream(StreamObserver<GatewayCommand> outbound) {
        return new AgentEventObserver(registry, router, pendingCommandRegistry, outbound);
    }
}
