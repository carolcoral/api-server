/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.config;

import com.carolcoral.apiserver.handler.MockWebSocketHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置类
 *
 * @author carolcoral
 */
@Tag(name = "WebSocket配置", description = "WebSocket配置类")
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    /**
     * 构造器
     */
    public WebSocketConfig(MockWebSocketHandler mockWebSocketHandler) {
        this.mockWebSocketHandler = mockWebSocketHandler;
    }


    private final MockWebSocketHandler mockWebSocketHandler;

    /**
     * 注册WebSocket处理器
     *
     * @param registry WebSocketHandlerRegistry
     */
    @Override
    @Operation(summary = "注册WebSocket处理器")
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mockWebSocketHandler, "/ws/mock/**")
                .setAllowedOrigins("*");
    }
}
