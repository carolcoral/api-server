/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.ChatCompletionRequest;
import com.carolcoral.apiserver.dto.ChatCompletionResponse;
import com.carolcoral.apiserver.entity.AiApiKey;
import com.carolcoral.apiserver.entity.AiModel;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.repository.AiApiKeyRepository;
import com.carolcoral.apiserver.repository.AiModelRepository;
import com.carolcoral.apiserver.service.AiProxyService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 代理控制器 — 对外 OpenAI 兼容接口
 * <p>
 * 提供 /v1/chat/completions 和 /v1/models 等 OpenAI 兼容端点，
 * 用户通过 API Key 鉴权后调用。
 * </p>
 *
 * @author carolcoral
 */
@Tag(name = "AI代理", description = "OpenAI 兼容的 AI 代理接口")
@RestController
@RequestMapping("/api/ai")
public class AiProxyController {

    private static final Logger log = LoggerFactory.getLogger(AiProxyController.class);

    private final AiProxyService aiProxyService;
    private final AiApiKeyRepository apiKeyRepository;
    private final AiModelRepository modelRepository;

    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    public AiProxyController(AiProxyService aiProxyService,
                             AiApiKeyRepository apiKeyRepository,
                             AiModelRepository modelRepository) {
        this.aiProxyService = aiProxyService;
        this.apiKeyRepository = apiKeyRepository;
        this.modelRepository = modelRepository;
    }

    /**
     * Chat Completion（非流式）
     */
    @Operation(summary = "Chat Completion", description = "OpenAI 兼容的 Chat Completion 接口")
    @PostMapping("/v1/chat/completions")
    public ChatCompletionResponse chatCompletions(
            @Parameter(description = "Chat Completion 请求") @RequestBody ChatCompletionRequest request,
            HttpServletRequest servletRequest) throws Exception {

        User user = authenticateUser(servletRequest);
        log.info("AI Chat: user={}, model={}", user.getUsername(), request.getModel());

        try {
            return aiProxyService.processChatCompletion(user, request);
        } catch (AiProxyService.QuotaExceededException e) {
            throw new RuntimeException("AI 调用额度已用尽，请等待额度重置或联系管理员");
        }
    }

    /**
     * Chat Completion（流式 SSE）
     */
    @Operation(summary = "Chat Completion (Stream)", description = "OpenAI 兼容的流式 Chat Completion 接口")
    @PostMapping(value = "/v1/chat/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatCompletionsStream(
            @Parameter(description = "Chat Completion 请求") @RequestBody ChatCompletionRequest request,
            HttpServletRequest servletRequest) {

        User user = authenticateUser(servletRequest);
        log.info("AI Chat Stream: user={}, model={}", user.getUsername(), request.getModel());

        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        streamExecutor.execute(() -> {
            try {
                aiProxyService.processStreamChatCompletion(user, request, chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException e) {
                        log.warn("SSE 发送失败: {}", e.getMessage());
                    }
                });
                emitter.complete();
            } catch (AiProxyService.QuotaExceededException e) {
                try {
                    emitter.send(SseEmitter.event().data("data: {\"error\":\"额度已用尽\"}\n\n"));
                    emitter.complete();
                } catch (IOException ignored) {}
            } catch (Exception e) {
                log.error("流式 AI 调用失败: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().data("data: {\"error\":\"" + e.getMessage() + "\"}\n\n"));
                    emitter.complete();
                } catch (IOException ignored) {}
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(throwable -> log.warn("SSE 异常: {}", throwable.getMessage()));

        return emitter;
    }

    /**
     * 列出可用模型
     */
    @Operation(summary = "列出模型", description = "返回当前用户可用的模型列表（OpenAI 兼容）")
    @GetMapping("/v1/models")
    public Map<String, Object> listModels(HttpServletRequest servletRequest) {
        User user = authenticateUser(servletRequest);

        List<AiModel> models = modelRepository.findByStatusTrue();
        List<Map<String, Object>> modelList = new ArrayList<>();
        for (AiModel model : models) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", model.getModelName());
            m.put("object", "model");
            m.put("owned_by", model.getProvider().getName());
            modelList.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("object", "list");
        result.put("data", modelList);
        return result;
    }

    /**
     * 全局异常处理 - 返回 OpenAI 兼容的错误 JSON
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("AI 接口异常: {}", e.getMessage());
        Map<String, Object> error = new LinkedHashMap<>();
        Map<String, Object> errDetail = new LinkedHashMap<>();
        errDetail.put("message", "AI 服务暂时不可用: " + e.getMessage());
        errDetail.put("type", "api_error");
        errDetail.put("code", "500");
        error.put("error", errDetail);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(error);
    }

    /**
     * 通过 API Key 鉴权
     */
    private User authenticateUser(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("缺少 Authorization header，请使用 Bearer sk-xxx 格式");
        }
        String apiKey = authHeader.substring(7).trim();
        Optional<AiApiKey> keyOpt = apiKeyRepository.findByApiKey(apiKey);
        if (keyOpt.isEmpty() || !keyOpt.get().getStatus()) {
            throw new RuntimeException("无效的 API Key");
        }
        AiApiKey key = keyOpt.get();
        key.setLastUsed(java.time.LocalDateTime.now());
        apiKeyRepository.save(key);
        return key.getUser();
    }
}
