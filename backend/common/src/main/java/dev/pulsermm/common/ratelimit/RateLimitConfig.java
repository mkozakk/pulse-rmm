package dev.pulsermm.common.ratelimit;

import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class RateLimitConfig {

    @Bean
    @Lazy
    public LettuceBasedProxyManager<String> rateLimitProxyManager(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port) {
        RedisClient client = RedisClient.create(RedisURI.builder().withHost(host).withPort(port).build());
        StatefulRedisConnection<String, byte[]> connection =
            client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        return LettuceBasedProxyManager.builderFor(connection).build();
    }
}
