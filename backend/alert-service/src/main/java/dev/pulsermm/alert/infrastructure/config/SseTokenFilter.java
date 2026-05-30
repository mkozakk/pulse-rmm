package dev.pulsermm.alert.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

// EventSource cannot send custom headers, so the webapp passes the JWT as ?token=.
// This filter promotes it into Authorization: Bearer before Spring Security runs.
@Component
public class SseTokenFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getParameter("token");
        if (token != null && !token.isBlank() && request.getHeader("Authorization") == null) {
            chain.doFilter(new BearerTokenRequest(request, token), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private static class BearerTokenRequest extends HttpServletRequestWrapper {
        private final String token;

        BearerTokenRequest(HttpServletRequest request, String token) {
            super(request);
            this.token = token;
        }

        @Override
        public String getHeader(String name) {
            if ("Authorization".equalsIgnoreCase(name)) return "Bearer " + token;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("Authorization".equalsIgnoreCase(name)) return Collections.enumeration(Collections.singleton("Bearer " + token));
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            var names = Collections.list(super.getHeaderNames());
            if (!names.contains("Authorization")) names.add("Authorization");
            return Collections.enumeration(names);
        }
    }
}
