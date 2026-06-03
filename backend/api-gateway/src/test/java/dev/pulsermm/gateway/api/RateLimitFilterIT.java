package dev.pulsermm.gateway.api;

import dev.pulsermm.common.ratelimit.RateLimitFilter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class RateLimitFilterIT {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.0-alpine").withExposedPorts(6379);

    @Mock
    FilterChain chain;

    private LettuceBasedProxyManager<String> proxyManager;
    private BucketConfiguration bucketConfig;

    private static final int CAPACITY = 5;
    private static final long REFILL_SECONDS = 60;

    @BeforeEach
    void setUp() {
        RedisClient client = RedisClient.create(
            RedisURI.builder()
                .withHost(redis.getHost())
                .withPort(redis.getMappedPort(6379))
                .build()
        );
        StatefulRedisConnection<String, byte[]> connection =
            client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        proxyManager = LettuceBasedProxyManager.builderFor(connection).build();
        bucketConfig = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder()
                .capacity(CAPACITY)
                .refillGreedy(CAPACITY, Duration.ofSeconds(REFILL_SECONDS))
                .build())
            .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestsWithinLimitAreAllowed() throws Exception {
        String userId = UUID.randomUUID().toString();
        authenticate(userId);
        var filter = new RateLimitFilter(proxyManager, bucketConfig, REFILL_SECONDS);

        for (int i = 0; i < CAPACITY; i++) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request("/api/groups"), response, chain);
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void requestExceedingLimitReturns429() throws Exception {
        String userId = UUID.randomUUID().toString();
        authenticate(userId);
        var filter = new RateLimitFilter(proxyManager, bucketConfig, REFILL_SECONDS);

        for (int i = 0; i < CAPACITY; i++) {
            filter.doFilter(request("/api/groups"), new MockHttpServletResponse(), chain);
        }

        var response = new MockHttpServletResponse();
        filter.doFilter(request("/api/groups"), response, chain);
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void retryAfterHeaderPresentOn429() throws Exception {
        String userId = UUID.randomUUID().toString();
        authenticate(userId);
        var filter = new RateLimitFilter(proxyManager, bucketConfig, REFILL_SECONDS);

        for (int i = 0; i < CAPACITY; i++) {
            filter.doFilter(request("/api/groups"), new MockHttpServletResponse(), chain);
        }

        var response = new MockHttpServletResponse();
        filter.doFilter(request("/api/groups"), response, chain);
        assertThat(response.getHeader("Retry-After")).isNotNull();
    }

    @Test
    void unauthenticatedRequestsRateLimitedByIp() throws Exception {
        String uniqueIp = "10.1." + (int) (Math.random() * 254) + "." + (int) (Math.random() * 254);
        var filter = new RateLimitFilter(proxyManager, bucketConfig, REFILL_SECONDS);

        for (int i = 0; i < CAPACITY; i++) {
            var req = request("/api/groups");
            req.setRemoteAddr(uniqueIp);
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }

        var req = request("/api/groups");
        req.setRemoteAddr(uniqueIp);
        var response = new MockHttpServletResponse();
        filter.doFilter(req, response, chain);
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void differentUsersHaveSeparateBuckets() throws Exception {
        String userA = UUID.randomUUID().toString();
        String userB = UUID.randomUUID().toString();
        var filter = new RateLimitFilter(proxyManager, bucketConfig, REFILL_SECONDS);

        authenticate(userA);
        for (int i = 0; i < CAPACITY; i++) {
            filter.doFilter(request("/api/groups"), new MockHttpServletResponse(), chain);
        }
        var responseA = new MockHttpServletResponse();
        filter.doFilter(request("/api/groups"), responseA, chain);
        assertThat(responseA.getStatus()).isEqualTo(429);

        authenticate(userB);
        var responseB = new MockHttpServletResponse();
        filter.doFilter(request("/api/groups"), responseB, chain);
        assertThat(responseB.getStatus()).isNotEqualTo(429);
    }

    private static MockHttpServletRequest request(String path) {
        var req = new MockHttpServletRequest("GET", path);
        req.setServletPath(path);
        req.setRemoteAddr("127.0.0.1");
        return req;
    }

    private static void authenticate(String userId) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(userId)
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt, Collections.emptyList())
        );
    }
}
