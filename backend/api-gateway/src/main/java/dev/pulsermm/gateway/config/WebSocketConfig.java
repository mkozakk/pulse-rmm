package dev.pulsermm.gateway.config;

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
    private final ShellHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(ShellWebSocketHandler shellHandler, ShellHandshakeInterceptor handshakeInterceptor) {
        this.shellHandler = shellHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(shellHandler, "/ws/shell/{endpointId}")
            .addInterceptors(handshakeInterceptor)
            .setAllowedOrigins("http://localhost:5173");
    }
}
