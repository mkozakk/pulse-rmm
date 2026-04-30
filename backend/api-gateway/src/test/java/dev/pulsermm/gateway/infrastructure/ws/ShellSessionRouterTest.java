package dev.pulsermm.gateway.infrastructure.ws;

import com.google.protobuf.ByteString;
import dev.pulsermm.proto.v1.AgentEvent;
import dev.pulsermm.proto.v1.ShellExited;
import dev.pulsermm.proto.v1.ShellOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.BinaryMessage;
import java.nio.ByteBuffer;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShellSessionRouterTest {

    private ShellSessionRouter router;

    @BeforeEach
    void setup() {
        router = new ShellSessionRouter();
    }

    @Test
    void routeShellOutputSendsBinaryFrameWithLeading01() throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        router.register("s1", ws);

        AgentEvent event = AgentEvent.newBuilder()
            .setShellOutput(ShellOutput.newBuilder()
                .setSessionId("s1")
                .setData(ByteString.copyFromUtf8("hi"))
                .build())
            .build();

        router.route("s1", event);

        BinaryMessage expected = new BinaryMessage(new byte[]{0x01, 'h', 'i'});
        verify(ws).sendMessage(argThat((BinaryMessage msg) -> {
            ByteBuffer buf = msg.getPayload().duplicate();
            byte[] sent = new byte[buf.remaining()];
            buf.get(sent);
            return sent.length == 3 && sent[0] == 0x01 && sent[1] == 'h' && sent[2] == 'i';
        }));
    }

    @Test
    void routeShellExitedClosesSession() throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        router.register("s1", ws);

        AgentEvent event = AgentEvent.newBuilder()
            .setShellExited(ShellExited.newBuilder().setSessionId("s1").setExitCode(0).build())
            .build();

        router.route("s1", event);

        verify(ws).close(CloseStatus.NORMAL);
    }

    @Test
    void routeUnknownSessionDoesNothing() throws Exception {
        AgentEvent event = AgentEvent.newBuilder()
            .setShellOutput(ShellOutput.newBuilder().setSessionId("missing").build())
            .build();

        router.route("missing", event);
        // No exception, no interactions to verify
    }

    @Test
    void removeUnregistersSession() throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        router.register("s1", ws);
        router.remove("s1");

        AgentEvent event = AgentEvent.newBuilder()
            .setShellOutput(ShellOutput.newBuilder()
                .setSessionId("s1")
                .setData(ByteString.copyFromUtf8("x"))
                .build())
            .build();

        router.route("s1", event);

        verify(ws, never()).sendMessage(any());
    }
}
