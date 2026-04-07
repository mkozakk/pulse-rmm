package dev.pulsermm.common.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RateLimitFilter extends OncePerRequestFilter {

    private final ProxyManager<String> proxyManager;
    private final BucketConfiguration bucketConfig;
    private final long refillSeconds;

    public RateLimitFilter(ProxyManager<String> proxyManager, BucketConfiguration bucketConfig, long refillSeconds) {
        this.proxyManager = proxyManager;
        this.bucketConfig = bucketConfig;
        this.refillSeconds = refillSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = resolveKey(request);
        var bucket = proxyManager.builder().build(key, () -> bucketConfig);
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(refillSeconds));
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\"}");
        }
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return "rate:user:" + jwt.getToken().getSubject();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return "rate:ip:" + ip;
    }
}
