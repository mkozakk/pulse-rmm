package dev.pulsermm.agenthub.config;

import dev.pulsermm.common.ratelimit.RateLimitFilter;
import dev.pulsermm.common.security.QueryParamBearerTokenResolver;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final BearerTokenResolver bearerTokenResolver;
    private final ProxyManager<String> rateLimitProxyManager;

    @Value("${pulse.rate-limit.capacity:200}")
    private long rateLimitCapacity;

    @Value("${pulse.rate-limit.refill-tokens:200}")
    private long rateLimitRefillTokens;

    @Value("${pulse.rate-limit.refill-seconds:60}")
    private long rateLimitRefillSeconds;

    public SecurityConfig(QueryParamBearerTokenResolver bearerTokenResolver,
                          @Lazy ProxyManager<String> rateLimitProxyManager) {
        this.bearerTokenResolver = bearerTokenResolver;
        this.rateLimitProxyManager = rateLimitProxyManager;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        BucketConfiguration bucketConfig = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder()
                .capacity(rateLimitCapacity)
                .refillGreedy(rateLimitRefillTokens, Duration.ofSeconds(rateLimitRefillSeconds))
                .build())
            .build();

        http
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/internal/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(bearerTokenResolver)
                .jwt(jwt -> {})
            )
            .addFilterAfter(new RateLimitFilter(rateLimitProxyManager, bucketConfig, rateLimitRefillSeconds),
                BearerTokenAuthenticationFilter.class)
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
