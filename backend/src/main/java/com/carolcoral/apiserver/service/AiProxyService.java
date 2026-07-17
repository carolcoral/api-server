/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ChatCompletionRequest;
import com.carolcoral.apiserver.dto.ChatCompletionResponse;
import com.carolcoral.apiserver.entity.*;
import com.carolcoral.apiserver.repository.AiModelRepository;
import com.carolcoral.apiserver.repository.AiProviderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * AI 代理核心服务
 * 负责转发请求到目标 AI 服务商，支持 fallback 自动切换
 *
 * @author carolcoral
 */
@Service
public class AiProxyService {

    private static final Logger log = LoggerFactory.getLogger(AiProxyService.class);

    private final ObjectMapper objectMapper;
    private final AiModelSelector modelSelector;
    private final AiQuotaService quotaService;
    private final AiUsageService usageService;
    private final AiModelRepository modelRepository;
    private final AiProviderRepository providerRepository;

    public AiProxyService(ObjectMapper objectMapper, AiModelSelector modelSelector,
                          AiQuotaService quotaService, AiUsageService usageService,
                          AiModelRepository modelRepository, AiProviderRepository providerRepository) {
        this.objectMapper = objectMapper;
        this.modelSelector = modelSelector;
        this.quotaService = quotaService;
        this.usageService = usageService;
        this.modelRepository = modelRepository;
        this.providerRepository = providerRepository;
    }

    /**
     * 处理 Chat Completion 请求（非流式）
     *
     * @param user    用户
     * @param request 请求
     * @return 响应
     */
    public ChatCompletionResponse processChatCompletion(User user, ChatCompletionRequest request) throws Exception {
        String modelName = request.getModel();
        String strategy = request.getFallbackStrategy();

        // 重置过期额度窗口
        quotaService.resetExpiredWindows();

        // 检查额度
        if (!quotaService.hasQuota(user.getId())) {
            throw new QuotaExceededException("AI 调用额度已用尽，请等待额度重置或联系管理员");
        }

        Set<Long> triedModelIds = new HashSet<>();
        int retries = 0;
        int maxRetries = modelSelector.getMaxFallbackRetries();

        AiSubscription currentSub = null;

        // Auto 模式：自动选择模型
        if ("auto".equals(modelName)) {
            List<AiSubscription> candidates = modelSelector.selectModels(user.getId(), strategy);
            if (candidates.isEmpty()) {
                throw new RuntimeException("没有可用的 AI 模型");
            }
            currentSub = candidates.get(0);
        } else {
            // 指定模型
            Optional<AiSubscription> subOpt = modelSelector.findSubscription(user.getId(), modelName);
            if (subOpt.isEmpty()) {
                throw new RuntimeException("未订阅模型: " + modelName);
            }
            currentSub = subOpt.get();
        }

        // 主调用 + fallback 重试
        while (currentSub != null) {
            AiModel model = currentSub.getModel();
            // 重新加载 provider 完整信息（包括 apiKey），避免 LAZY 加载导致 apiKey 为空
            Long providerId = currentSub.getProvider().getId();
            AiProvider provider = providerRepository.findById(providerId)
                    .orElseThrow(() -> new RuntimeException("未找到服务商: " + providerId));
            triedModelIds.add(model.getId());

            try {
                ChatCompletionResponse response = doChatRequest(provider, model, request);
                modelSelector.markModelSuccess(model, response.getUsage() != null ? 1000 : 0);

                // 扣减额度
                if (response.getUsage() != null) {
                    quotaService.deductTokens(user.getId(), response.getUsage().getTotalTokens());
                }

                // 记录日志
                Long fallbackFrom = retries > 0 ? triedModelIds.stream().skip(retries - 1).findFirst().orElse(null) : null;
                usageService.logSuccess(user, provider, model,
                        objectMapper.writeValueAsString(request), response,
                        response.getUsage() != null ? 1000 : 0, fallbackFrom);

                return response;

            } catch (Exception e) {
                log.warn("AI 调用失败: model={}, error={}", model.getModelName(), e.getMessage());
                modelSelector.markModelFailure(model);
                usageService.logFailure(user, provider, model,
                        objectMapper.writeValueAsString(request), 502, e.getMessage(), 0);

                retries++;
                if (retries > maxRetries) break;

                // Fallback 到下一个模型
                List<AiSubscription> fallbacks = modelSelector.getFallbackCandidates(
                        user.getId(), strategy, triedModelIds);
                currentSub = fallbacks.isEmpty() ? null : fallbacks.get(0);

                if (currentSub != null) {
                    log.info("Fallback 切换到: {} -> {}", model.getModelName(),
                            currentSub.getModel().getModelName());
                }
            }
        }

        throw new RuntimeException("所有 AI 模型均不可用，请稍后重试");
    }

    /**
     * 执行真实的 Chat 请求
     */
    private ChatCompletionResponse doChatRequest(AiProvider provider, AiModel model,
                                                  ChatCompletionRequest request) throws Exception {
        String apiUrl = buildApiUrl(provider.getBaseUrl());

        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(120000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");

        // 认证 - 统一使用 Bearer 前缀（OpenAI 协议标准）
        if (provider.getAuthType() != null && !"none".equalsIgnoreCase(provider.getAuthType())) {
            if (provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
                connection.setRequestProperty("Authorization", "Bearer " + provider.getApiKey());
            }
        }

        // 构造请求体（使用实际模型名）
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model.getModelName());
        requestBody.put("messages", request.getMessages());
        if (request.getTemperature() != null) requestBody.put("temperature", request.getTemperature());
        if (request.getMaxTokens() != null) requestBody.put("max_tokens", request.getMaxTokens());
        else if (model.getMaxTokens() != null) requestBody.put("max_tokens", model.getMaxTokens());
        if (request.getTopP() != null) requestBody.put("top_p", request.getTopP());
        if (request.getStop() != null) requestBody.put("stop", request.getStop());

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int statusCode = connection.getResponseCode();
        if (statusCode >= 400) {
            String errorBody = readErrorStream(connection);
            throw new RuntimeException("AI 服务返回错误 " + statusCode + ": " + errorBody);
        }

        String responseBody = readResponseBody(connection);
        connection.disconnect();

        return objectMapper.readValue(responseBody, ChatCompletionResponse.class);
    }

    /**
     * 处理流式 Chat Completion
     */
    public void processStreamChatCompletion(User user, ChatCompletionRequest request,
                                             java.util.function.Consumer<String> chunkConsumer) throws Exception {
        String modelName = request.getModel();
        String strategy = request.getFallbackStrategy();

        quotaService.resetExpiredWindows();

        if (!quotaService.hasQuota(user.getId())) {
            throw new QuotaExceededException("AI 调用额度已用尽");
        }

        AiSubscription currentSub;
        if ("auto".equals(modelName)) {
            List<AiSubscription> candidates = modelSelector.selectModels(user.getId(), strategy);
            if (candidates.isEmpty()) throw new RuntimeException("没有可用的 AI 模型");
            currentSub = candidates.get(0);
        } else {
            Optional<AiSubscription> subOpt = modelSelector.findSubscription(user.getId(), modelName);
            if (subOpt.isEmpty()) throw new RuntimeException("未订阅模型: " + modelName);
            currentSub = subOpt.get();
        }

        // 重新加载 provider 完整信息（包括 apiKey），避免 LAZY 加载导致 apiKey 为空
        Long providerId = currentSub.getProvider().getId();
        AiProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("未找到服务商: " + providerId));
        doStreamRequest(provider, currentSub.getModel(), request, chunkConsumer);

        modelSelector.markModelSuccess(currentSub.getModel(), 0);
    }

    /**
     * 执行流式请求
     */
    private void doStreamRequest(AiProvider provider, AiModel model, ChatCompletionRequest request,
                                  java.util.function.Consumer<String> chunkConsumer) throws Exception {
        String apiUrl = buildApiUrl(provider.getBaseUrl());

        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(300000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");

        if (provider.getAuthType() != null && !"none".equalsIgnoreCase(provider.getAuthType())) {
            if (provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
                connection.setRequestProperty("Authorization", "Bearer " + provider.getApiKey());
            }
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model.getModelName());
        requestBody.put("messages", request.getMessages());
        requestBody.put("stream", true);
        if (request.getTemperature() != null) requestBody.put("temperature", request.getTemperature());
        if (request.getMaxTokens() != null) requestBody.put("max_tokens", request.getMaxTokens());
        else if (model.getMaxTokens() != null) requestBody.put("max_tokens", model.getMaxTokens());

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int statusCode = connection.getResponseCode();
        if (statusCode >= 400) {
            String errorBody = readErrorStream(connection);
            throw new RuntimeException("AI 服务返回错误 " + statusCode + ": " + errorBody);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if ("[DONE]".equals(data)) {
                        chunkConsumer.accept("data: [DONE]\n\n");
                        break;
                    }
                    chunkConsumer.accept(line + "\n");
                }
            }
        }
        connection.disconnect();
    }

    private String buildApiUrl(String baseUrl) {
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (!url.endsWith("/v1")) {
            url += "/v1";
        }
        return url + "/chat/completions";
    }

    private String readResponseBody(HttpURLConnection connection) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String readErrorStream(HttpURLConnection connection) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getErrorStream() != null
                        ? connection.getErrorStream() : connection.getInputStream(),
                        StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /**
     * 内部流式调用（用于 AI 对话页面通过订阅模型中转，避免 HTTP 循环）
     * 不走用户鉴权和额度检查，不检查模型健康状态，直接转发到目标服务商
     */
    public java.io.BufferedReader processStreamChatInternal(String modelName,
                                                             List<Map<String, String>> messages) throws Exception {
        // 从所有启用状态的订阅中查找（不检查健康状态，因为内部调用应直接转发）
        List<AiSubscription> allSubs = modelSelector.getAllEnabledSubscriptionsIgnoreHealth();
        AiSubscription targetSub = null;
        for (AiSubscription sub : allSubs) {
            if (sub.getModel().getModelName().equals(modelName) || sub.getModel().getDisplayName().equals(modelName)) {
                targetSub = sub;
                break;
            }
        }
        if (targetSub == null) {
            throw new RuntimeException("未找到订阅模型: " + modelName);
        }

        AiModel model = targetSub.getModel();
        // 重新加载 provider 完整信息（包括 apiKey），避免 LAZY 加载导致 apiKey 为空
        Long providerId = targetSub.getProvider().getId();
        AiProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("未找到服务商: " + providerId));
        String apiUrl = buildApiUrl(provider.getBaseUrl());

        log.info("AI Stream 内部路由: provider={}, model={}, apiUrl={}", provider.getName(), model.getModelName(), apiUrl);

        // 构造请求体
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model.getModelName());
        requestBody.put("messages", messages);
        requestBody.put("stream", true);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(300000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");

        if (provider.getAuthType() != null && !"none".equalsIgnoreCase(provider.getAuthType())) {
            if (provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
                connection.setRequestProperty("Authorization", "Bearer " + provider.getApiKey());
                log.debug("AI Stream 使用 Authorization: Bearer {}...", provider.getApiKey().substring(0, Math.min(8, provider.getApiKey().length())));
            } else {
                log.warn("AI Stream 服务商 {} 缺少 API Key", provider.getName());
            }
        }

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int statusCode = connection.getResponseCode();
        if (statusCode >= 400) {
            String errorBody = readErrorStream(connection);
            throw new RuntimeException("AI 服务返回错误 " + statusCode + ": " + errorBody);
        }

        return new java.io.BufferedReader(
                new java.io.InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
    }

    /**
     * 额度超限异常
     */
    public static class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String message) {
            super(message);
        }
    }
}
