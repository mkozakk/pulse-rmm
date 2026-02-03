package dev.pulsermm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "pulse.jwt.secret=test-secret-key-that-is-long-enough-for-hs256",
    "grpc.server.port=0"
})
class GatewayRoutingTest {

    @Test
    void contextLoads() {
    }
}
