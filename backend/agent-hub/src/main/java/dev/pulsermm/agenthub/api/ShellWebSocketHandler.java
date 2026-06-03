package dev.pulsermm.agenthub.api;

import com.google.protobuf.ByteString;
import dev.pulsermm.agenthub.infrastructure.grpc.AgentRegistry;
import dev.pulsermm.agenthub.infrastructure.ws.ShellSessionRouter;
import dev.pulsermm.proto.v1.CloseShell;
import dev.pulsermm.proto.v1.GatewayCommand;
import dev.pulsermm.proto.v1.OpenShell;
import dev.pulsermm.proto.v1.ShellInput;
import dev.pulsermm.proto.v1.ShellResize;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;

@Component
public class ShellWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ShellWebSocketHandler.class);

    static final String ATTR_SESSION_ID = "sessionId";
    static final String ATTR_ENDPOINT_ID = "endpointId";

    private final AgentRegistry registry;
    private final ShellSessionRouter router;

    public ShellWebSocketHandler(AgentRegistry registry, ShellSessionRouter router) {
        this.registry = registry;
        this.router = router;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) throws IOException {
        String endpointId = (String) ws.getAttributes().get(ATTR_ENDPOINT_ID);
        UUID endpointUuid;
        try {
            endpointUuid = UUID.fromString(endpointId);
        } catch (IllegalArgumentException e) {
            ws.close(CloseStatus.BAD_DATA.withReason("invalid endpoint id"));
            return;
        }

        Optional<StreamObserver<GatewayCommand>> agent = registry.get(endpointUuid);
        if (agent.isEmpty()) {
            ws.close(CloseStatus.SERVER_ERROR.withReason("endpoint offline"));
            return;
        }

        String sessionId = UUID.randomUUID().toString();
        ws.getAttributes().put(ATTR_SESSION_ID, sessionId);
        router.register(sessionId, ws);

        agent.get().onNext(GatewayCommand.newBuilder()
            .setOpenShell(OpenShell.newBuilder()
                .setSessionId(sessionId)
                .setCols(80)
                .setRows(24)
                .build())
            .build());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession ws, BinaryMessage message) throws IOException {
        ByteBuffer payload = message.getPayload();
        if (payload.remaining() < 1) return;

        String sessionId = (String) ws.getAttributes().get(ATTR_SESSION_ID);
        String endpointId = (String) ws.getAttributes().get(ATTR_ENDPOINT_ID);
        if (sessionId == null || endpointId == null) return;

        StreamObserver<GatewayCommand> agent = registry.get(UUID.fromString(endpointId)).orElse(null);
        if (agent == null) {
            ws.close(CloseStatus.SERVER_ERROR.withReason("endpoint offline"));
            return;
        }

        byte type = payload.get();
        if (type == 0x01) {
            byte[] data = new byte[payload.remaining()];
            payload.get(data);
            agent.onNext(GatewayCommand.newBuilder()
                .setShellInput(ShellInput.newBuilder()
                    .setSessionId(sessionId)
                    .setData(ByteString.copyFrom(data))
                    .build())
                .build());
        } else if (type == 0x02 && payload.remaining() >= 4) {
            int cols = payload.getShort() & 0xFFFF;
            int rows = payload.getShort() & 0xFFFF;
            agent.onNext(GatewayCommand.newBuilder()
                .setShellResize(ShellResize.newBuilder()
                    .setSessionId(sessionId)
                    .setCols(cols)
                    .setRows(rows)
                    .build())
                .build());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        String sessionId = (String) ws.getAttributes().get(ATTR_SESSION_ID);
        String endpointId = (String) ws.getAttributes().get(ATTR_ENDPOINT_ID);
        if (sessionId == null) return;

        router.remove(sessionId);

        if (endpointId != null) {
            registry.get(UUID.fromString(endpointId)).ifPresent(agent -> {
                try {
                    agent.onNext(GatewayCommand.newBuilder()
                        .setCloseShell(CloseShell.newBuilder().setSessionId(sessionId).build())
                        .build());
                } catch (Exception e) {
                    logger.debug("Failed to send CloseShell for {}: {}", sessionId, e.getMessage());
                }
            });
        }
    }
}
