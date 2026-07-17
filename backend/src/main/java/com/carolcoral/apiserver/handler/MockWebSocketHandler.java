/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.handler;

import com.carolcoral.apiserver.dto.MockRequest;
import com.carolcoral.apiserver.dto.MockResponseDTO;
import com.carolcoral.apiserver.service.MockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock WebSocket处理器
 *
 * @author carolcoral
 */
@Tag(name = "WebSocket处理器", description = "Mock WebSocket请求处理器")
@Component
public class MockWebSocketHandler extends TextWebSocketHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MockWebSocketHandler.class);

    /**
     * 构造器
     */
    public MockWebSocketHandler(MockService mockService,
        ObjectMapper objectMapper) {
        this.mockService = mockService;
        this.objectMapper = objectMapper;
    }

    private final MockService mockService;
    private final ObjectMapper objectMapper;

    // 存储WebSocket会话
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    @Operation(summary = "连接建立")
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("WebSocket连接建立: {}", sessionId);

        // 发送连接成功消息
        Map<String, Object> connectMsg = Map.of(
                "type", "connected",
                "sessionId", sessionId,
                "message", "WebSocket连接成功"
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(connectMsg)));
    }

    @Override
    @Operation(summary = "处理文本消息")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String sessionId = session.getId();
        
        log.info("WebSocket收到消息: sessionId={}, payload={}", sessionId, payload);

        try {
            // 解析消息
            Map<String, Object> msgMap = objectMapper.readValue(payload, Map.class);
            String type = (String) msgMap.get("type");

            if ("mock".equals(type)) {
                // 处理Mock请求
                handleMockRequest(session, msgMap);
            } else if ("ping".equals(type)) {
                // 处理心跳
                handlePing(session);
            } else {
                // 未知消息类型
                sendError(session, "未知的消息类型: " + type);
            }

        } catch (Exception e) {
            log.error("处理WebSocket消息失败: {}", e.getMessage(), e);
            sendError(session, "处理消息失败: " + e.getMessage());
        }
    }

    @Override
    @Operation(summary = "连接关闭")
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        log.info("WebSocket连接关闭: {}, 状态: {}", sessionId, status);
    }

    @Override
    @Operation(summary = "处理传输错误")
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        log.error("WebSocket传输错误: {}", sessionId, exception);
        sendError(session, "传输错误: " + exception.getMessage());
    }

    /**
     * 处理Mock请求
     *
     * @param session WebSocket会话
     * @param msgMap  消息Map
     */
    private void handleMockRequest(WebSocketSession session, Map<String, Object> msgMap) throws Exception {
        try {
            // 构建Mock请求
            MockRequest mockRequest = new MockRequest();
            mockRequest.setProjectCode((String) msgMap.get("projectCode"));
            mockRequest.setPath((String) msgMap.get("path"));
            mockRequest.setMethod((String) msgMap.get("method"));
            mockRequest.setParams((Map<String, Object>) msgMap.get("params"));
            mockRequest.setBody(msgMap.get("body"));

            // 处理请求
            MockResponseDTO mockResponse = mockService.handleMockRequest(mockRequest);

            // 发送响应
            Map<String, Object> responseMsg = Map.of(
                    "type", "response",
                    "statusCode", mockResponse.getStatusCode(),
                    "headers", mockResponse.getHeaders(),
                    "body", mockResponse.getBody(),
                    "delay", mockResponse.getDelay()
            );

            // 模拟延迟（限制最大延迟时间）
            if (mockResponse.getDelay() != null && mockResponse.getDelay() > 0) {
                long delay = Math.min(mockResponse.getDelay(), 5000);
                Thread.sleep(delay);
            }

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(responseMsg)));

        } catch (Exception e) {
            log.error("处理Mock请求失败: {}", e.getMessage(), e);
            sendError(session, "处理Mock请求失败: " + e.getMessage());
        }
    }

    /**
     * 处理心跳
     *
     * @param session WebSocket会话
     */
    private void handlePing(WebSocketSession session) throws Exception {
        Map<String, Object> pongMsg = Map.of(
                "type", "pong",
                "timestamp", System.currentTimeMillis()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pongMsg)));
    }

    /**
     * 发送错误消息
     *
     * @param session WebSocket会话
     * @param message 错误消息
     */
    private void sendError(WebSocketSession session, String message) throws Exception {
        Map<String, Object> errorMsg = Map.of(
                "type", "error",
                "message", message,
                "timestamp", System.currentTimeMillis()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMsg)));
    }

    /**
     * 广播消息给所有连接的客户端
     *
     * @param message 消息
     */
    @Operation(summary = "广播消息")
    public void broadcastMessage(String message) {
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (Exception e) {
                log.error("广播消息失败: {}", e.getMessage());
            }
        });
    }
}
