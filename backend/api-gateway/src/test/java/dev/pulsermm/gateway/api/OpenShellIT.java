package dev.pulsermm.gateway.api;

import dev.pulsermm.gateway.infrastructure.grpc.AgentRegistry;
import dev.pulsermm.gateway.infrastructure.ws.ShellSessionRouter;
import dev.pulsermm.proto.v1.AgentEvent;
import dev.pulsermm.proto.v1.GatewayCommand;
import dev.pulsermm.proto.v1.ShellOutput;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import javax.crypto.SecretKey;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "pulse.jwt.secret=test-secret-key-that-is-long-enough-for-hs256",
    "grpc.server.port=0"
})
class OpenShellIT {

    @LocalServerPort
    int port;

    @Autowired
    AgentRegistry agentRegistry;

    @Autowired
    ShellSessionRouter router;

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private UUID endpointId;
    private FakeAgentObserver fakeAgent;

    @BeforeEach
    void setup() {
        endpointId = UUID.randomUUID();
        fakeAgent = new FakeAgentObserver();
    }

    @Test
    void noTokenRejectsWithUnauthorized() {
        StandardWebSocketClient client = new StandardWebSocketClient();
        TestWsHandler handler = new TestWsHandler();

        assertThatThrownBy(() ->
            client.doHandshake(handler, wsHeaders(), wsUri(endpointId, ""))
                .completable().get(3, TimeUnit.SECONDS)
        ).isInstanceOf(ExecutionException.class);
    }

    @Test
    void juniorTechnicianRejectsWithForbidden() {
        String jwt = buildJwt(List.of("junior_technician"));
        StandardWebSocketClient client = new StandardWebSocketClient();
        TestWsHandler handler = new TestWsHandler();

        assertThatThrownBy(() ->
            client.doHandshake(handler, wsHeaders(), wsUri(endpointId, jwt))
                .completable().get(3, TimeUnit.SECONDS)
        ).isInstanceOf(ExecutionException.class);
    }

    @Test
    void endpointOfflineClosesWithServerError() throws Exception {
        String jwt = buildJwt(List.of("admin"));
        StandardWebSocketClient client = new StandardWebSocketClient();
        TestWsHandler handler = new TestWsHandler();

        client.doHandshake(handler, wsHeaders(), wsUri(endpointId, jwt))
            .completable().get(3, TimeUnit.SECONDS);

        CloseStatus status = handler.closes.poll(3, TimeUnit.SECONDS);
        assertThat(status).isNotNull();
        assertThat(status.getCode()).isEqualTo(CloseStatus.SERVER_ERROR.getCode()); // 1011
    }

    @Test
    void adminConnectsAndOpenShellDeliveredToAgent() throws Exception {
        agentRegistry.register(endpointId, fakeAgent);

        String jwt = buildJwt(List.of("admin"));
        StandardWebSocketClient client = new StandardWebSocketClient();
        TestWsHandler handler = new TestWsHandler();

        WebSocketSession ws = client.doHandshake(handler, wsHeaders(), wsUri(endpointId, jwt))
            .completable().get(3, TimeUnit.SECONDS);

        GatewayCommand cmd = fakeAgent.received.poll(3, TimeUnit.SECONDS);
        assertThat(cmd).isNotNull();
        assertThat(cmd.hasOpenShell()).isTrue();
        assertThat(cmd.getOpenShell().getSessionId()).isNotBlank();

        ws.close();
    }

    @Test
    void inputFrameDeliveredAsShellInput() throws Exception {
        agentRegistry.register(endpointId, fakeAgent);

        String jwt = buildJwt(List.of("admin"));
        StandardWebSocketClient client = new StandardWebSocketClient();
        TestWsHandler handler = new TestWsHandler();

        WebSocketSession ws = client.doHandshake(handler, wsHeaders(), wsUri(endpointId, jwt))
            .completable().get(3, TimeUnit.SECONDS);

        // consume the OpenShell first
        GatewayCommand openCmd = fakeAgent.received.poll(3, TimeUnit.SECONDS);
        assertThat(openCmd).isNotNull();

        // send 0x01 + "ls\n"
        byte[] frame = new byte[]{'l', 's', '\n'};
        ByteBuffer buf = ByteBuffer.allocate(frame.length + 1);
        buf.put((byte) 0x01);
        buf.put(frame);
        ws.sendMessage(new BinaryMessage(buf.array()));

        GatewayCommand inputCmd = fakeAgent.received.poll(3, TimeUnit.SECONDS);
        assertThat(inputCmd).isNotNull();
        assertThat(inputCmd.hasShellInput()).isTrue();
        assertThat(inputCmd.getShellInput().getData().toByteArray()).isEqualTo(frame);

        ws.close();
    }

    @Test
    void shellOutputFromAgentReachesWsClient() throws Exception {
        agentRegistry.register(endpointId, fakeAgent);

        String jwt = buildJwt(List.of("admin"));
        StandardWebSocketClient client = new StandardWebSocketClient();
        TestWsHandler handler = new TestWsHandler();

        client.doHandshake(handler, wsHeaders(), wsUri(endpointId, jwt))
            .completable().get(3, TimeUnit.SECONDS);

        GatewayCommand openCmd = fakeAgent.received.poll(3, TimeUnit.SECONDS);
        String sessionId = openCmd.getOpenShell().getSessionId();

        // simulate agent sending output via router
        router.route(sessionId, AgentEvent.newBuilder()
            .setShellOutput(ShellOutput.newBuilder()
                .setSessionId(sessionId)
                .setData(ByteString.copyFromUtf8("hello"))
                .build())
            .build());

        byte[] received = handler.frames.poll(3, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received[0]).isEqualTo((byte) 0x01);
        assertThat(new String(received, 1, received.length - 1)).isEqualTo("hello");
    }

    @Test
    void wsCloseDeliversCloseShellToAgent() throws Exception {
        agentRegistry.register(endpointId, fakeAgent);

        String jwt = buildJwt(List.of("admin"));
        StandardWebSocketClient client = new StandardWebSocketClient();
        TestWsHandler handler = new TestWsHandler();

        WebSocketSession ws = client.doHandshake(handler, wsHeaders(), wsUri(endpointId, jwt))
            .completable().get(3, TimeUnit.SECONDS);

        fakeAgent.received.poll(3, TimeUnit.SECONDS); // consume OpenShell

        ws.close(CloseStatus.NORMAL);

        GatewayCommand closeCmd = fakeAgent.received.poll(3, TimeUnit.SECONDS);
        assertThat(closeCmd).isNotNull();
        assertThat(closeCmd.hasCloseShell()).isTrue();
    }

    // --- helpers ---

    private String buildJwt(List<String> roles) {
        return Jwts.builder()
            .subject("test-user")
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .claim("roles", roles)
            .signWith(KEY)
            .compact();
    }

    private WebSocketHttpHeaders wsHeaders() {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("http://localhost:5173");
        return headers;
    }

    private URI wsUri(UUID epId, String token) {
        return URI.create("ws://localhost:" + port + "/ws/shell/" + epId + "?token=" + token);
    }

    static class FakeAgentObserver implements StreamObserver<GatewayCommand> {
        final BlockingQueue<GatewayCommand> received = new LinkedBlockingQueue<>();
        @Override public void onNext(GatewayCommand cmd) { received.add(cmd); }
        @Override public void onError(Throwable t) {}
        @Override public void onCompleted() {}
    }

    static class TestWsHandler extends AbstractWebSocketHandler {
        final BlockingQueue<byte[]> frames = new LinkedBlockingQueue<>();
        final BlockingQueue<CloseStatus> closes = new LinkedBlockingQueue<>();

        @Override
        protected void handleBinaryMessage(WebSocketSession ws, BinaryMessage msg) {
            byte[] bytes = new byte[msg.getPayload().remaining()];
            msg.getPayload().get(bytes);
            frames.add(bytes);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
            closes.add(status);
        }
    }
}
