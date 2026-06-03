package dev.pulsermm.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

@Component
public class QueryParamBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String fromHeader = delegate.resolve(request);
        if (fromHeader != null) {
            return fromHeader;
        }
        // browsers can't set an Authorization header on WebSocket upgrades, so the
        // token is passed as ?token= on the /ws/** handshake URL instead
        String param = request.getParameter("token");
        return (param != null && !param.isBlank()) ? param : null;
    }
}
