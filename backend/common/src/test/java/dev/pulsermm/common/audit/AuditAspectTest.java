package dev.pulsermm.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditAspectTest {

    private final RecordingPublisher publisher = new RecordingPublisher();
    private final AuditAspect aspect = new AuditAspect(publisher, new ObjectMapper());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publishesOnSuccess() {
        setUser("550e8400-e29b-41d4-a716-446655440000");
        proxy(new TestService()).doSomething("request-body");

        assertThat(publisher.published).hasSize(1);
        AuditEventMessage msg = publisher.published.get(0);
        assertThat(msg.action()).isEqualTo("test.action");
        assertThat(msg.permissionUsed()).isEqualTo("test:perm");
        assertThat(msg.userId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(msg.payloadJson()).contains("request-body");
    }

    @Test
    void doesNotPublishWhenMethodThrows() {
        setUser("550e8400-e29b-41d4-a716-446655440000");
        assertThrows(RuntimeException.class, () -> proxy(new TestService()).failingMethod());
        assertThat(publisher.published).isEmpty();
    }

    @Test
    void extractsEndpointId() {
        setUser("550e8400-e29b-41d4-a716-446655440000");
        UUID endpointId = UUID.randomUUID();
        proxy(new TestService()).withEndpoint(endpointId, "payload");

        assertThat(publisher.published).hasSize(1);
        assertThat(publisher.published.get(0).endpointId()).isEqualTo(endpointId);
    }

    @Test
    void skipsPayloadWhenCapturePayloadFalse() {
        setUser("550e8400-e29b-41d4-a716-446655440000");
        proxy(new TestService()).noPayload("secret");

        assertThat(publisher.published).hasSize(1);
        assertThat(publisher.published.get(0).payloadJson()).isNull();
    }

    private TestService proxy(TestService target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    private void setUser(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(userId, null, List.of())
        );
    }

    static class RecordingPublisher extends AuditPublisher {
        final List<AuditEventMessage> published = new ArrayList<>();

        RecordingPublisher() {
            super(null);
        }

        @Override
        public void publish(AuditEventMessage message) {
            published.add(message);
        }
    }

    static class TestService {

        @Auditable(action = "test.action", permission = "test:perm")
        public void doSomething(String body) {}

        @Auditable(action = "test.fail", permission = "test:perm")
        public void failingMethod() {
            throw new RuntimeException("boom");
        }

        @Auditable(action = "test.endpoint", permission = "test:perm")
        public void withEndpoint(@EndpointId UUID endpointId, String body) {}

        @Auditable(action = "test.nopayload", permission = "test:perm", capturePayload = false)
        public void noPayload(String secret) {}
    }
}
