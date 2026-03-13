package com.terra.api.realtime.config;

import com.terra.api.config.CorsProperties;
import com.terra.api.realtime.websocket.RealtimeWebSocketHandler;
import com.terra.api.realtime.websocket.WebSocketHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RealtimeWebSocketHandler realtimeWebSocketHandler;
    private final WebSocketHandshakeInterceptor webSocketHandshakeInterceptor;
    private final CorsProperties corsProperties;

    public WebSocketConfig(RealtimeWebSocketHandler realtimeWebSocketHandler,
                           WebSocketHandshakeInterceptor webSocketHandshakeInterceptor,
                           CorsProperties corsProperties) {
        this.realtimeWebSocketHandler = realtimeWebSocketHandler;
        this.webSocketHandshakeInterceptor = webSocketHandshakeInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(realtimeWebSocketHandler, "/api/ws")
                .addInterceptors(webSocketHandshakeInterceptor)
                .setAllowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new));
    }
}
