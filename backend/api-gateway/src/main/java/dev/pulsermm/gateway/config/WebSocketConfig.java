package dev.pulsermm.gateway.config;

import dev.pulsermm.gateway.api.DesktopSignalingHandshakeInterceptor;
import dev.pulsermm.gateway.api.DesktopSignalingWebSocketHandler;
import dev.pulsermm.gateway.api.ShellHandshakeInterceptor;
import dev.pulsermm.gateway.api.ShellWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ShellWebSocketHandler shellHandler;
    private final ShellHandshakeInterceptor shellInterceptor;
    private final DesktopSignalingWebSocketHandler desktopHandler;
    private final DesktopSignalingHandshakeInterceptor desktopInterceptor;

    public WebSocketConfig(ShellWebSocketHandler shellHandler, ShellHandshakeInterceptor shellInterceptor,
                           DesktopSignalingWebSocketHandler desktopHandler,
                           DesktopSignalingHandshakeInterceptor desktopInterceptor) {
        this.shellHandler = shellHandler;
        this.shellInterceptor = shellInterceptor;
        this.desktopHandler = desktopHandler;
        this.desktopInterceptor = desktopInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(shellHandler, "/ws/shell/{endpointId}")
            .addInterceptors(shellInterceptor)
            .setAllowedOrigins("http://localhost:5173");

        registry.addHandler(desktopHandler, "/ws/sessions/*/signal")
            .addInterceptors(desktopInterceptor)
            .setAllowedOrigins("http://localhost:5173");
    }
}
