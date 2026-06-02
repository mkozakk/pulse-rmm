package dev.pulsermm.gateway.api;

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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final PermissionGuard permissionGuard;
    private final BearerTokenResolver bearerTokenResolver;
    private final ProxyManager<String> rateLimitProxyManager;
    private final dev.pulsermm.gateway.infrastructure.identity.EndpointOrgClient endpointOrgClient;

    @Value("${pulse.rate-limit.capacity:200}")
    private long rateLimitCapacity;

    @Value("${pulse.rate-limit.refill-tokens:200}")
    private long rateLimitRefillTokens;

    @Value("${pulse.rate-limit.refill-seconds:60}")
    private long rateLimitRefillSeconds;

    public SecurityConfig(PermissionGuard permissionGuard, BearerTokenResolver bearerTokenResolver,
                          @Lazy ProxyManager<String> rateLimitProxyManager,
                          dev.pulsermm.gateway.infrastructure.identity.EndpointOrgClient endpointOrgClient) {
        this.permissionGuard = permissionGuard;
        this.bearerTokenResolver = bearerTokenResolver;
        this.rateLimitProxyManager = rateLimitProxyManager;
        this.endpointOrgClient = endpointOrgClient;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Collections.singletonList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", "Accept", "Origin"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
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
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(bearerTokenResolver)
                .jwt(jwt -> {})
            )
            .addFilterAfter(new RateLimitFilter(rateLimitProxyManager, bucketConfig, rateLimitRefillSeconds),
                BearerTokenAuthenticationFilter.class)
            .addFilterAfter(new StructurePermissionFilter(permissionGuard), RateLimitFilter.class)
            .addFilterAfter(new AlertPermissionFilter(permissionGuard), StructurePermissionFilter.class)
            .addFilterAfter(new ApiPermissionFilter(permissionGuard), AlertPermissionFilter.class)
            .addFilterAfter(new OrgContextFilter(endpointOrgClient), ApiPermissionFilter.class)
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
