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
import com.carolcoral.apiserver.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
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

    /**
     * 默认系统提示词，注入到所有转发请求中。
     * 压制客户端（如 WorkBuddy）注入的工具调用/联网搜索指令。
     */
    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是一个直接回答问题的通用助手。请根据你的知识直接回答问题，不要尝试进行联网搜索，"
            + "不要调用任何外部工具或 API，不要输出任何工具调用格式（如 <tool_code> 或类似标记）。"
            + "如果信息不足，请如实说明，并给出基于已有知识的建议。";

    private static final Logger log = LoggerFactory.getLogger(AiProxyService.class);

    private final ObjectMapper objectMapper;
    private final AiModelSelector modelSelector;
    private final AiQuotaService quotaService;
    private final AiUsageService usageService;
    private final AiModelRepository modelRepository;
    private final AiProviderRepository providerRepository;
    private final UserRepository userRepository;

    public AiProxyService(ObjectMapper objectMapper, AiModelSelector modelSelector,
                          AiQuotaService quotaService, AiUsageService usageService,
                          AiModelRepository modelRepository, AiProviderRepository providerRepository,
                          UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.modelSelector = modelSelector;
        this.quotaService = quotaService;
        this.usageService = usageService;
        this.modelRepository = modelRepository;
        this.providerRepository = providerRepository;
        this.userRepository = userRepository;
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

        // Auto 模式：自动选择模型（当 model=auto 或订阅的模型是 autoMode 时）
        boolean isAutoMode = "auto".equals(modelName);
        if (isAutoMode) {
            List<AiSubscription> candidates = modelSelector.selectModels(user.getId(), strategy);
            if (candidates.isEmpty()) {
                throw new RuntimeException("没有可用的 AI 模型");
            }
            currentSub = candidates.get(0);
        } else {
            // 指定模型：查找订阅，如果该模型是 autoMode 则也进入自动选择
            Optional<AiSubscription> subOpt = modelSelector.findSubscription(user.getId(), modelName);
            if (subOpt.isEmpty()) {
                throw new RuntimeException("未订阅模型: " + modelName);
            }
            AiSubscription sub = subOpt.get();
            if (sub.getModel().getAutoMode() != null && sub.getModel().getAutoMode()) {
                // 订阅的是自动模式模型，从所有订阅中自动选择最优模型
                isAutoMode = true;
                List<AiSubscription> candidates = modelSelector.selectModels(user.getId(), strategy);
                if (candidates.isEmpty()) {
                    throw new RuntimeException("没有可用的 AI 模型");
                }
                currentSub = candidates.get(0);
            } else {
                currentSub = sub;
            }
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
                modelSelector.markModelSuccessAsync(model.getId(), response.getUsage() != null ? 1000 : 0);

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
                modelSelector.markModelFailureAsync(model.getId());
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

        // 构造请求体（使用实际模型名），注入默认 system prompt 压制工具调用
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model.getModelName());
        requestBody.put("messages", injectDefaultSystemPrompt(request.getMessages()));
        if (request.getTemperature() != null) requestBody.put("temperature", request.getTemperature());
        if (request.getMaxTokens() != null) requestBody.put("max_tokens", request.getMaxTokens());
        else if (model.getMaxTokens() != null) requestBody.put("max_tokens", model.getMaxTokens());
        if (request.getTopP() != null) requestBody.put("top_p", request.getTopP());
        if (request.getStop() != null) requestBody.put("stop", request.getStop());

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        log.info("AI Chat 请求: apiUrl={}, model={}, body={}", apiUrl, model.getModelName(), jsonBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (provider.getAuthType() != null && !"none".equalsIgnoreCase(provider.getAuthType())) {
            if (provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
                headers.set("Authorization", "Bearer " + provider.getApiKey());
            }
        }

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("AI Chat 响应: status={}, bodyLength={}, elapsed={}ms",
                    response.getStatusCode().value(),
                    response.getBody() != null ? response.getBody().length() : 0,
                    elapsed);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return objectMapper.readValue(response.getBody(), ChatCompletionResponse.class);
            } else {
                String errorBody = response.getBody() != null ? response.getBody() : "";
                throw new RuntimeException("AI 服务返回错误 " + response.getStatusCode().value() + ": " + errorBody);
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("AI Chat 网络连接失败: {}", e.getMessage());
            throw new RuntimeException("AI API 连接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步处理流式 Chat Completion（在 SseEmitter 异步线程中调用）。
     * 数据库写操作（额度检查/扣减/日志）使用 synchronized 串行化，避免 SQLite 并发写冲突。
     */
    public void processStreamChatCompletionAsync(Long userId, String modelName, String strategy,
                                                   ChatCompletionRequest request,
                                                   java.util.function.Consumer<String> chunkConsumer) throws Exception {
        // 串行化额度检查，避免并发窗口重置/读取冲突
        synchronized (this) {
            quotaService.resetExpiredWindows();
            if (!quotaService.hasQuota(userId)) {
                throw new QuotaExceededException("AI 调用额度已用尽");
            }
        }

        AiSubscription currentSub;
        boolean isAutoMode = "auto".equals(modelName);
        if (isAutoMode) {
            List<AiSubscription> candidates = modelSelector.selectModels(userId, strategy);
            if (candidates.isEmpty()) throw new RuntimeException("没有可用的 AI 模型");
            currentSub = candidates.get(0);
        } else {
            Optional<AiSubscription> subOpt = modelSelector.findSubscription(userId, modelName);
            if (subOpt.isEmpty()) throw new RuntimeException("未订阅模型: " + modelName);
            AiSubscription sub = subOpt.get();
            if (sub.getModel().getAutoMode() != null && sub.getModel().getAutoMode()) {
                isAutoMode = true;
                List<AiSubscription> candidates = modelSelector.selectModels(userId, strategy);
                if (candidates.isEmpty()) throw new RuntimeException("没有可用的 AI 模型");
                currentSub = candidates.get(0);
            } else {
                currentSub = sub;
            }
        }

        // 提取 ID，通过 repository 重新加载实体
        Long modelId = currentSub.getModel().getId();
        Long providerId = currentSub.getProvider().getId();

        AiModel model = modelRepository.findById(modelId)
                .orElseThrow(() -> new RuntimeException("未找到模型: " + modelId));
        AiProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("未找到服务商: " + providerId));

        long startTime = System.currentTimeMillis();
        StreamResult streamResult = doStreamRequest(provider, model, request, chunkConsumer);
        long latencyMs = System.currentTimeMillis() - startTime;

        int promptTokens = streamResult.promptTokens;
        int completionTokens = streamResult.completionTokens;
        int totalTokens = streamResult.totalTokens;

        // 上游未返回 usage 时，按文本长度估算
        if (totalTokens <= 0) {
            StringBuilder promptText = new StringBuilder();
            if (request.getMessages() != null) {
                for (ChatCompletionRequest.Message message : request.getMessages()) {
                    if (message != null && message.getContentSafe() != null) {
                        promptText.append(message.getContentSafe()).append("\n");
                    }
                }
            }
            promptTokens = estimateTokens(promptText.toString());
            completionTokens = estimateTokens(streamResult.completionText);
            totalTokens = promptTokens + completionTokens;
        }

        // 串行化数据库写操作，避免 SQLite SQLITE_BUSY_SNAPSHOT
        synchronized (this) {
            // 扣减额度
            quotaService.deductTokens(userId, totalTokens);

            // 记录流式调用使用率
            try {
                User user = userRepository.getReferenceById(userId);
                double cost = 0;
                if (model.getInputPrice() != null) {
                    cost += model.getInputPrice() * promptTokens / 1000.0;
                }
                if (model.getOutputPrice() != null) {
                    cost += model.getOutputPrice() * completionTokens / 1000.0;
                }
                cost = Math.round(cost * 1000000.0) / 1000000.0;
                usageService.logStreamSuccess(user, provider, model,
                        objectMapper.writeValueAsString(request), promptTokens, completionTokens,
                        totalTokens, latencyMs, cost);
            } catch (Exception e) {
                log.warn("记录流式使用率失败: {}", e.getMessage());
            }
        }

        modelSelector.markModelSuccessAsync(modelId, latencyMs);
    }

    /**
     * 处理流式 Chat Completion（同步，JWT 鉴权场景使用）
     * @deprecated 对外订阅请使用 processStreamChatCompletionAsync
     */
    @Deprecated
    public void processStreamChatCompletion(User user, ChatCompletionRequest request,
                                             java.util.function.Consumer<String> chunkConsumer) throws Exception {
        processStreamChatCompletionAsync(user.getId(), request.getModel(),
                request.getFallbackStrategy(), request, chunkConsumer);
    }

    /**
     * 流式调用结果统计
     */
    private static class StreamResult {
        int promptTokens;
        int completionTokens;
        int totalTokens;
        String completionText;
    }

    /**
     * 执行流式请求
     */
    private StreamResult doStreamRequest(AiProvider provider, AiModel model, ChatCompletionRequest request,
                                          java.util.function.Consumer<String> chunkConsumer) throws Exception {
        String apiUrl = buildApiUrl(provider.getBaseUrl());

        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(300000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "text/event-stream");

        log.info("AI Stream 请求: apiUrl={}, model={}", apiUrl, model.getModelName());

        if (provider.getAuthType() != null && !"none".equalsIgnoreCase(provider.getAuthType())) {
            if (provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
                connection.setRequestProperty("Authorization", "Bearer " + provider.getApiKey());
            }
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model.getModelName());
        requestBody.put("messages", injectDefaultSystemPrompt(request.getMessages()));
        requestBody.put("stream", true);
        requestBody.put("stream_options", Map.of("include_usage", true));
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
            log.error("AI 服务返回错误: statusCode={}, body={}", statusCode, errorBody);
            throw new RuntimeException("AI 服务返回错误 " + statusCode + ": " + errorBody);
        }

        StreamResult result = new StreamResult();
        StringBuilder completionText = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if ("[DONE]".equals(data)) {
                        // 只传递 [DONE] 标记，由上层 SseEmitter 包装
                        chunkConsumer.accept("[DONE]");
                        break;
                    }
                    // 解析 usage 与增量内容，用于统计 token
                    try {
                        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(data);
                        com.fasterxml.jackson.databind.JsonNode usageNode = node.get("usage");
                        if (usageNode != null && !usageNode.isNull() && usageNode.isObject()) {
                            result.promptTokens = usageNode.has("prompt_tokens") ? usageNode.get("prompt_tokens").asInt(0) : 0;
                            result.completionTokens = usageNode.has("completion_tokens") ? usageNode.get("completion_tokens").asInt(0) : 0;
                            result.totalTokens = usageNode.has("total_tokens") ? usageNode.get("total_tokens").asInt(0) : 0;
                        }
                        com.fasterxml.jackson.databind.JsonNode choices = node.get("choices");
                        if (choices != null && choices.isArray() && choices.size() > 0) {
                            com.fasterxml.jackson.databind.JsonNode delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content")) {
                                String content = delta.get("content").asText("");
                                if (content != null) {
                                    completionText.append(content);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("解析 SSE chunk 统计失败: {}", e.getMessage());
                    }
                    // 只传递 JSON 内容（去掉 data: 前缀），由上层 SseEmitter 包装 data:
                    chunkConsumer.accept(data);
                }
            }
        }
        connection.disconnect();
        result.completionText = completionText.toString();
        return result;
    }

    private String buildApiUrl(String baseUrl) {
        String url = baseUrl.trim();
        // 移除尾部斜杠
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        // 移除已知的 API 路径后缀，只保留 base URL
        if (url.endsWith("/chat/completions")) {
            url = url.substring(0, url.length() - "/chat/completions".length());
        }
        if (url.endsWith("/responses")) {
            url = url.substring(0, url.length() - "/responses".length());
        }
        if (url.endsWith("/v1/responses")) {
            url = url.substring(0, url.length() - "/v1/responses".length());
        }
        // 确保以 /v1 结尾
        if (!url.endsWith("/v1")) {
            url += "/v1";
        }
        return url + "/chat/completions";
    }

    /**
     * 简单估算 token 数（中文按字，英文按 4 字符 ≈ 1 token）。
     * 没有 tiktoken 时用于流式响应统计兜底。
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chinese = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') {
                chinese++;
            } else {
                other++;
            }
        }
        return chinese + (other / 4) + 1;
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
        // 处理 auto 模式：优先从已启用订阅中找具体模型，没有再从全局启用模型中选择
        if ("auto".equals(modelName)) {
            AiModel selectedModel = null;
            AiProvider provider = null;

            // 1. 优先从启用订阅中找具体模型（内部路由通过订阅中转，不强制要求模型状态为 true）
            List<AiSubscription> allSubs = modelSelector.getAllEnabledSubscriptionsIgnoreHealth();
            java.util.Optional<AiSubscription> concreteSub = allSubs.stream()
                    .filter(s -> s.getModel().getAutoMode() == null || !s.getModel().getAutoMode())
                    .findFirst();
            if (concreteSub.isPresent()) {
                selectedModel = concreteSub.get().getModel();
                provider = concreteSub.get().getProvider();
                log.info("AI Stream 内部路由(auto模式): 从订阅中选择 model={}, provider={}",
                        selectedModel.getModelName(), provider.getName());
            }

            // 2. 订阅中无具体模型，退而从全局启用模型中选择
            if (selectedModel == null) {
                List<AiModel> allModels = modelRepository.findByStatusTrueWithProvider();
                List<AiModel> candidateModels = allModels.stream()
                        .filter(m -> m.getAutoMode() == null || !m.getAutoMode())
                        .collect(java.util.stream.Collectors.toList());
                
                log.info("AI Stream 内部路由(auto模式): 全局候选模型数={}, 模型列表={}",
                        candidateModels.size(),
                        candidateModels.stream().map(m -> m.getModelName() + "(" + m.getHealthStatus() + ")").collect(java.util.stream.Collectors.toList()));
                
                if (candidateModels.isEmpty()) {
                    throw new RuntimeException("auto 模式下没有可用的具体模型（共" + allModels.size() + "个启用模型，"
                            + allModels.stream().filter(m -> m.getAutoMode() != null && m.getAutoMode()).count() + "个是auto模式标记）");
                }
                
                selectedModel = candidateModels.get(0);
                provider = selectedModel.getProvider();
            }

            // 重新加载 provider 完整信息（包括 apiKey），避免 LAZY 加载导致 apiKey 为空
            Long providerId = provider.getId();
            provider = providerRepository.findById(providerId)
                    .orElseThrow(() -> new RuntimeException("未找到服务商: " + providerId));

            String apiUrl = buildApiUrl(provider.getBaseUrl());
            log.info("AI Stream 内部路由(auto模式): provider={}, model={}, apiUrl={}", provider.getName(), selectedModel.getModelName(), apiUrl);
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", selectedModel.getModelName());
            requestBody.put("messages", messages);
            requestBody.put("stream", true);
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(300000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream");
            if (provider.getAuthType() != null && !"none".equalsIgnoreCase(provider.getAuthType())) {
                if (provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
                    connection.setRequestProperty("Authorization", "Bearer " + provider.getApiKey());
                }
            }
            try (java.io.OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
            }
            int statusCode = connection.getResponseCode();
            if (statusCode >= 400) {
                String errorBody = readErrorStream(connection);
                throw new RuntimeException("AI 服务返回错误 " + statusCode + ": " + errorBody);
            }
            return new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
        }

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

        // 如果匹配到的模型是 autoMode，从所有已订阅的非 auto 模型中选择第一个
        if (model.getAutoMode() != null && model.getAutoMode()) {
            // 从当前用户订阅中找一个非 autoMode 的可用模型
            AiSubscription concreteSub = allSubs.stream()
                    .filter(s -> s.getModel().getAutoMode() == null || !s.getModel().getAutoMode())
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("auto 模式下没有可用的具体模型"));
            model = concreteSub.getModel();
            targetSub = concreteSub;
        }

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
        connection.setRequestProperty("Accept", "text/event-stream");

        log.info("AI Stream 请求: apiUrl={}, model={}", apiUrl, model.getModelName());

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
     * 在消息列表开头注入默认 system prompt（如果还没有的话）。
     * 用于压制客户端（如 WorkBuddy）注入的工具调用/联网搜索指令。
     */
    private List<ChatCompletionRequest.Message> injectDefaultSystemPrompt(
            List<ChatCompletionRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) return messages;
        boolean hasSystem = messages.stream()
                .anyMatch(m -> "system".equalsIgnoreCase(m.getRole()));
        if (hasSystem) return messages;
        List<ChatCompletionRequest.Message> augmented = new ArrayList<>(messages.size() + 1);
        augmented.add(new ChatCompletionRequest.Message("system", DEFAULT_SYSTEM_PROMPT));
        augmented.addAll(messages);
        return augmented;
    }

    /**
     * 检查当前实例是否有可用的 AI 订阅。
     * 用于判断是否应走内部路由：有订阅才能内部转发，无订阅则走外部 HTTP 请求。
     */
    public boolean hasAvailableSubscriptions() {
        try {
            List<AiSubscription> subs = modelSelector.getAllEnabledSubscriptionsIgnoreHealth();
            return subs != null && !subs.isEmpty();
        } catch (Exception e) {
            log.warn("检查可用订阅失败: {}", e.getMessage());
            return false;
        }
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
