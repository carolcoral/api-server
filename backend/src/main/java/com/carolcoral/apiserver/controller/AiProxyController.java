/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ChatCompletionRequest;
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
     * Chat Completion — 统一使用 SSE 流式返回（OpenAI 兼容）
     * 对外订阅地址的核心接口，仅需 API Key 鉴权。
     * 在请求线程中完成所有 Hibernate 数据加载，避免异步线程中 LAZY 代理失效。
     */
    @Operation(summary = "Chat Completion (SSE 流式)", description = "OpenAI 兼容的流式 Chat Completion 接口，仅需 API Key 鉴权。不限制 produces 以适应不同客户端 Accept 头。")
    @PostMapping(value = "/v1/chat/completions")
    public SseEmitter chatCompletions(
            @Parameter(description = "Chat Completion 请求") @RequestBody ChatCompletionRequest request,
            HttpServletRequest servletRequest) {

        User user = authenticateUser(servletRequest);
        String clientIp = servletRequest.getRemoteAddr();
        String userAgent = servletRequest.getHeader("User-Agent");
        log.info("AI Chat Stream: user={}, model={}, clientIp={}, userAgent={}", user.getUsername(), request.getModel(), clientIp, userAgent);

        // 在请求线程中完成所有数据加载，提取为简单参数传给异步任务
        final Long userId = user.getId();
        final String modelName = request.getModel();
        final String strategy = request.getFallbackStrategy();

        SseEmitter emitter = new SseEmitter(300000L);

        streamExecutor.execute(() -> {
            try {
                aiProxyService.processStreamChatCompletionAsync(userId, modelName, strategy, request, chunk -> {
                    try {
                        // 手动构建 SSE 格式，确保 data: 后面有空格（兼容前端解析）
                        emitter.send(SseEmitter.event().data(chunk, MediaType.APPLICATION_JSON));
                    } catch (IOException e) {
                        log.warn("SSE 发送失败: {}", e.getMessage());
                    }
                });
                emitter.complete();
            } catch (AiProxyService.QuotaExceededException e) {
                try {
                    emitter.send(SseEmitter.event().data("{\"error\":\"额度已用尽\"}", MediaType.APPLICATION_JSON));
                    emitter.complete();
                } catch (IOException ignored) {}
            } catch (Exception e) {
                log.error("流式 AI 调用失败: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().data("{\"error\":\"" + e.getMessage() + "\"}", MediaType.APPLICATION_JSON));
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
     * 使用 produces 强制指定 JSON，避免客户端 Accept 头导致的内容协商失败
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e, HttpServletRequest request) {
        log.error("AI 接口异常: {}, clientIp={}, userAgent={}", e.getMessage(),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        Map<String, Object> error = new LinkedHashMap<>();
        Map<String, Object> errDetail = new LinkedHashMap<>();
        errDetail.put("message", "AI 服务暂时不可用: " + e.getMessage());
        errDetail.put("type", "api_error");
        errDetail.put("code", "500");
        error.put("error", errDetail);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(error);
    }

    /**
     * 通过 API Key 鉴权
     * 注意：last_used 更新改为异步，避免并发写入锁死 SQLite
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
        // 异步更新 last_used，避免每个请求都同步写库
        final Long keyId = key.getId();
        streamExecutor.execute(() -> {
            try {
                apiKeyRepository.findById(keyId).ifPresent(k -> {
                    k.setLastUsed(java.time.LocalDateTime.now());
                    apiKeyRepository.save(k);
                });
            } catch (Exception ignored) {
                // 静默失败，不影响业务
            }
        });
        return key.getUser();
    }
}
