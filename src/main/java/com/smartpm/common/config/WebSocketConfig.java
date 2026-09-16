package com.smartpm.common.config;

import com.smartpm.common.websocket.TaskWebSocketHandler;
import com.smartpm.common.websocket.WebSocketHandshakeInterceptor;
import com.smartpm.common.utils.JWTUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

/**
 * 注册 WebSocket 端点：/ws/project/{projectId}
 * WebSocketHandshakeInterceptor 在握手阶段校验 token 参数。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TaskWebSocketHandler taskWebSocketHandler;
    private final JWTUtil jwtUtil;

    @Value("${smartpm.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(taskWebSocketHandler, "/ws/project/{projectId}")
                .addInterceptors(new WebSocketHandshakeInterceptor(jwtUtil))
                .setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                        .filter(value -> !value.isEmpty()).toArray(String[]::new));
        registry.addHandler(taskWebSocketHandler, "/ws/notifications")
                .addInterceptors(new WebSocketHandshakeInterceptor(jwtUtil))
                .setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                        .filter(value -> !value.isEmpty()).toArray(String[]::new));
    }
}
