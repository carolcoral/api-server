/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.*;
import com.carolcoral.apiserver.entity.*;
import com.carolcoral.apiserver.repository.*;
import com.carolcoral.apiserver.service.AiHealthCheckService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AI 管理后台控制器
 *
 * @author carolcoral
 */
@Tag(name = "AI管理", description = "AI 服务商、模型、订阅、额度管理")
@RestController
@RequestMapping("/api/admin/ai")
public class AiAdminController {

    private static final Logger log = LoggerFactory.getLogger(AiAdminController.class);

    private final AiProviderRepository providerRepository;
    private final AiModelRepository modelRepository;
    private final AiSubscriptionRepository subscriptionRepository;
    private final AiQuotaRepository quotaRepository;
    private final AiUsageLogRepository usageLogRepository;
    private final AiApiKeyRepository apiKeyRepository;
    private final AiHealthCheckService healthCheckService;
    private final UserRepository userRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiAdminController(AiProviderRepository providerRepository,
                              AiModelRepository modelRepository,
                              AiSubscriptionRepository subscriptionRepository,
                              AiQuotaRepository quotaRepository,
                              AiUsageLogRepository usageLogRepository,
                              AiApiKeyRepository apiKeyRepository,
                              AiHealthCheckService healthCheckService,
                              UserRepository userRepository) {
        this.providerRepository = providerRepository;
        this.modelRepository = modelRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.quotaRepository = quotaRepository;
        this.usageLogRepository = usageLogRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.healthCheckService = healthCheckService;
        this.userRepository = userRepository;
    }

    // ==================== 服务商管理 ====================

    @Operation(summary = "查看服务商列表", description = "获取所有AI服务商列表")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ai-service:view')")
    @GetMapping("/providers")
    public ApiResponse<List<AiProvider>> listProviders() {
        return ApiResponse.success(providerRepository.findAll());
    }

    @Operation(summary = "创建服务商", description = "创建新的AI服务商配置")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ai-service:create')")
    @PostMapping("/providers")
    public ApiResponse<AiProvider> createProvider(@RequestBody AiProviderDTO dto) {
        AiProvider provider = new AiProvider();
        provider.setName(dto.getName());
        provider.setCode(dto.getCode());
        provider.setBaseUrl(dto.getBaseUrl());
        provider.setApiType(dto.getApiType());
        provider.setAuthType(dto.getAuthType());
        provider.setApiKey(dto.getApiKey());
        provider.setStatus(dto.getStatus());
        provider.setDescription(dto.getDescription());
        return ApiResponse.success(providerRepository.save(provider));
    }

    @Operation(summary = "更新服务商", description = "更新AI服务商配置信息")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ai-service:edit')")
    @PutMapping("/providers/{id}")
    public ApiResponse<AiProvider> updateProvider(@PathVariable Long id, @RequestBody AiProviderDTO dto) {
        AiProvider provider = providerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("服务商不存在"));
        provider.setName(dto.getName());
        provider.setBaseUrl(dto.getBaseUrl());
        provider.setApiType(dto.getApiType());
        provider.setAuthType(dto.getAuthType());
        if (dto.getApiKey() != null && !dto.getApiKey().isEmpty()) {
            provider.setApiKey(dto.getApiKey());
        }
        provider.setStatus(dto.getStatus());
        provider.setDescription(dto.getDescription());
        return ApiResponse.success(providerRepository.save(provider));
    }

    @Operation(summary = "删除服务商", description = "删除AI服务商及其关联的模型、订阅数据")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ai-service:delete')")
    @DeleteMapping("/providers/{id}")
    public ApiResponse<Void> deleteProvider(@PathVariable Long id) {
        // 1. 删除该服务商下的所有模型
        List<AiModel> models = modelRepository.findByProviderId(id);
        modelRepository.deleteAll(models);

        // 2. 删除该服务商下的所有订阅
        List<AiSubscription> subscriptions = subscriptionRepository.findByProviderId(id);
        subscriptionRepository.deleteAll(subscriptions);

        // 3. 删除服务商本身
        providerRepository.deleteById(id);
        return ApiResponse.success();
    }

    // ==================== 模型管理 ====================

    @Operation(summary = "查看模型列表", description = "获取指定服务商的模型列表")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ai-service:view')")
    @GetMapping("/providers/{providerId}/models")
    public ApiResponse<List<AiModel>> listModels(@PathVariable Long providerId) {
        return ApiResponse.success(modelRepository.findByProviderId(providerId));
    }

    @PostMapping("/providers/{providerId}/models")
    public ApiResponse<AiModel> createModel(@PathVariable Long providerId, @RequestBody AiModelDTO dto) {
        AiProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("服务商不存在"));
        AiModel model = new AiModel();
        model.setProvider(provider);
        model.setModelName(dto.getModelName());
        model.setDisplayName(dto.getDisplayName());
        model.setInputPrice(dto.getInputPrice());
        model.setOutputPrice(dto.getOutputPrice());
        model.setMaxTokens(dto.getMaxTokens());
        model.setSupportsStream(dto.getSupportsStream());
        model.setAutoMode(dto.getAutoMode() != null ? dto.getAutoMode() : false);
        model.setStatus(dto.getStatus());
        return ApiResponse.success(modelRepository.save(model));
    }

    @PutMapping("/providers/{providerId}/models/{id}")
    public ApiResponse<AiModel> updateModel(@PathVariable Long providerId, @PathVariable Long id,
                                             @RequestBody AiModelDTO dto) {
        AiModel model = modelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模型不存在"));
        model.setModelName(dto.getModelName());
        model.setDisplayName(dto.getDisplayName());
        model.setInputPrice(dto.getInputPrice());
        model.setOutputPrice(dto.getOutputPrice());
        model.setMaxTokens(dto.getMaxTokens());
        model.setSupportsStream(dto.getSupportsStream());
        model.setAutoMode(dto.getAutoMode() != null ? dto.getAutoMode() : false);
        model.setStatus(dto.getStatus());
        return ApiResponse.success(modelRepository.save(model));
    }

    @DeleteMapping("/providers/{providerId}/models/{id}")
    public ApiResponse<Void> deleteModel(@PathVariable Long providerId, @PathVariable Long id) {
        modelRepository.deleteById(id);
        return ApiResponse.success();
    }

    /**
     * 从服务商远程获取模型列表（调用 OpenAI /v1/models 接口）
     */
    @Operation(summary = "获取远程模型列表", description = "从服务商的 /v1/models 端点获取可用模型列表")
    @PostMapping("/providers/{providerId}/fetch-models")
    public ApiResponse<List<String>> fetchRemoteModels(@PathVariable Long providerId) {
        AiProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("服务商不存在"));

        List<String> models = new ArrayList<>();
        try {
            String baseUrl = provider.getBaseUrl();
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

            // 尝试多个可能的端点
            String[] endpoints = {"/v1/models", "/models"};
            String responseBody = null;

            for (String endpoint : endpoints) {
                try {
                    String modelsUrl = baseUrl + endpoint;
                    HttpURLConnection conn = (HttpURLConnection) new URL(modelsUrl).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);

                    if (provider.getApiKey() != null && !provider.getApiKey().isEmpty()) {
                        conn.setRequestProperty("Authorization", "Bearer " + provider.getApiKey());
                    }

                    int status = conn.getResponseCode();
                    if (status >= 200 && status < 300) {
                        responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                        conn.disconnect();
                        break;
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    log.debug("尝试端点 {} 失败: {}", endpoint, e.getMessage());
                }
            }

            if (responseBody == null) {
                return ApiResponse.error("无法连接到服务商 API，请检查接口地址和 API Key");
            }

            JsonNode root = objectMapper.readTree(responseBody);

            // OpenAI 格式: {"object":"list","data":[{"id":"model1",...}]}
            JsonNode data = root.get("data");
            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    JsonNode id = item.get("id");
                    if (id != null && !id.asText().isBlank()) {
                        models.add(id.asText());
                    }
                }
            }

            // Ollama 格式: {"models":[{"name":"model1",...}]}
            if (models.isEmpty() && root.has("models")) {
                JsonNode modelsNode = root.get("models");
                if (modelsNode.isArray()) {
                    for (JsonNode item : modelsNode) {
                        JsonNode name = item.has("name") ? item.get("name") : item.get("model");
                        if (name != null && !name.asText().isBlank()) {
                            models.add(name.asText());
                        }
                    }
                }
            }

            if (models.isEmpty()) {
                return ApiResponse.error("未能从响应中解析到模型列表，请确认服务商接口兼容 OpenAI 模型列表格式");
            }

            log.info("从服务商 {} 获取到 {} 个远程模型", provider.getName(), models.size());
            return ApiResponse.success(models);
        } catch (Exception e) {
            log.error("获取远程模型列表失败: {}", e.getMessage());
            return ApiResponse.error("获取远程模型列表失败: " + e.getMessage());
        }
    }

    /**
     * 批量创建模型
     */
    @Operation(summary = "批量添加模型", description = "一次性添加多个模型到指定服务商")
    @PostMapping("/providers/{providerId}/models/batch")
    public ApiResponse<List<AiModel>> batchCreateModels(@PathVariable Long providerId,
                                                         @RequestBody List<String> modelNames) {
        AiProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("服务商不存在"));
        List<AiModel> created = new ArrayList<>();
        int skipped = 0;
        for (String modelName : modelNames) {
            String trimmed = modelName.trim();
            if (trimmed.isEmpty()) continue;
            if (modelRepository.findByProviderIdAndModelName(providerId, trimmed).isPresent()) {
                skipped++;
                continue;
            }
            AiModel model = new AiModel();
            model.setProvider(provider);
            model.setModelName(trimmed);
            model.setDisplayName(trimmed);
            model.setSupportsStream(true);
            model.setStatus(true);
            created.add(modelRepository.save(model));
        }
        log.info("批量创建模型: provider={}, created={}, skipped={}", provider.getName(), created.size(), skipped);
        return ApiResponse.success(created);
    }

    @GetMapping("/models/health")
    public ApiResponse<List<Map<String, Object>>> getModelsHealth() {
        List<AiModel> models = modelRepository.findByStatusTrue();
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiModel model : models) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", model.getId());
            m.put("modelName", model.getModelName());
            m.put("providerName", model.getProvider().getName());
            m.put("healthStatus", model.getHealthStatus());
            m.put("avgLatencyMs", model.getAvgLatencyMs());
            m.put("consecutiveFailures", model.getConsecutiveFailures());
            m.put("cooldownUntil", model.getCooldownUntil());
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/models/{id}/health-check")
    public ApiResponse<AiModelHealth> healthCheck(@PathVariable Long id) {
        AiModel model = modelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模型不存在"));
        return ApiResponse.success(healthCheckService.checkModel(model));
    }

    // ==================== 订阅管理 ====================

    @GetMapping("/subscriptions")
    public ApiResponse<List<Map<String, Object>>> listSubscriptions(
            @RequestParam(required = false) Long userId) {
        List<AiSubscription> subs;
        if (userId != null) {
            // 使用 JOIN FETCH 避免 LAZY 加载问题（模型/服务商已删除时不会崩溃）
            subs = subscriptionRepository.findByUserIdAndStatusTrueWithModelAndProvider(userId);
        } else {
            subs = subscriptionRepository.findByStatusTrueWithModelAndProvider();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiSubscription sub : subs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", sub.getId());
            m.put("user", safeUserRef(sub.getUser()));
            m.put("provider", safeProviderRef(sub.getProvider()));
            m.put("model", safeModelRef(sub.getModel()));
            m.put("priority", sub.getPriority());
            m.put("weight", sub.getWeight());
            m.put("tags", sub.getTags());
            m.put("fallbackEnabled", sub.getFallbackEnabled());
            m.put("maxTokensPerRequest", sub.getMaxTokensPerRequest());
            m.put("status", sub.getStatus());
            m.put("createTime", sub.getCreateTime());
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    private Map<String, Object> safeUserRef(User user) {
        if (user == null) return Map.of("id", 0, "username", "未知");
        return Map.of("id", user.getId(), "username", user.getUsername() != null ? user.getUsername() : "未知");
    }

    private Map<String, Object> safeProviderRef(AiProvider provider) {
        if (provider == null) return Map.of("id", 0, "name", "已删除", "code", "");
        return Map.of("id", provider.getId(), "name", provider.getName() != null ? provider.getName() : "", "code", provider.getCode() != null ? provider.getCode() : "");
    }

    private Map<String, Object> safeModelRef(AiModel model) {
        if (model == null) return Map.of("id", 0, "modelName", "已删除", "displayName", "已删除");
        return Map.of("id", model.getId(), "modelName", model.getModelName() != null ? model.getModelName() : "", "displayName", model.getDisplayName() != null ? model.getDisplayName() : "");
    }

    @PostMapping("/subscriptions")
    public ApiResponse<AiSubscription> createSubscription(@RequestBody AiSubscriptionDTO dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        AiProvider provider = providerRepository.findById(dto.getProviderId())
                .orElseThrow(() -> new RuntimeException("服务商不存在"));
        AiModel model = modelRepository.findById(dto.getModelId())
                .orElseThrow(() -> new RuntimeException("模型不存在"));

        if (subscriptionRepository.existsByUserIdAndModelId(dto.getUserId(), dto.getModelId())) {
            return ApiResponse.error("该用户已订阅此模型");
        }

        AiSubscription sub = new AiSubscription();
        sub.setUser(user);
        sub.setProvider(provider);
        sub.setModel(model);
        sub.setPriority(dto.getPriority());
        sub.setWeight(dto.getWeight());
        sub.setTags(dto.getTags());
        sub.setFallbackEnabled(dto.getFallbackEnabled());
        sub.setMaxTokensPerRequest(dto.getMaxTokensPerRequest());
        sub.setStatus(dto.getStatus());
        return ApiResponse.success(subscriptionRepository.save(sub));
    }

    @PutMapping("/subscriptions/{id}")
    public ApiResponse<AiSubscription> updateSubscription(@PathVariable Long id, @RequestBody AiSubscriptionDTO dto) {
        AiSubscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订阅不存在"));
        sub.setPriority(dto.getPriority());
        sub.setWeight(dto.getWeight());
        sub.setTags(dto.getTags());
        sub.setFallbackEnabled(dto.getFallbackEnabled());
        sub.setMaxTokensPerRequest(dto.getMaxTokensPerRequest());
        sub.setStatus(dto.getStatus());
        return ApiResponse.success(subscriptionRepository.save(sub));
    }

    @PutMapping("/subscriptions/{id}/priority")
    public ApiResponse<AiSubscription> updatePriority(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        AiSubscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订阅不存在"));
        if (body.containsKey("priority")) sub.setPriority(body.get("priority"));
        if (body.containsKey("weight")) sub.setWeight(body.get("weight"));
        return ApiResponse.success(subscriptionRepository.save(sub));
    }

    @DeleteMapping("/subscriptions/{id}")
    public ApiResponse<Void> deleteSubscription(@PathVariable Long id) {
        subscriptionRepository.deleteById(id);
        return ApiResponse.success();
    }

    // ==================== 额度管理 ====================

    @GetMapping("/quotas")
    public ApiResponse<List<Map<String, Object>>> listQuotas(@RequestParam(required = false) Long userId) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (userId != null) {
            // 按用户查询
            List<AiQuota> quotas = quotaRepository.findByUserIdAndStatusTrue(userId);
            for (AiQuota q : quotas) {
                result.add(quotaToMap(q));
            }
            return ApiResponse.success(result);
        }

        // 查询所有启用用户
        List<User> enabledUsers = userRepository.findByEnabledTrue();
        // 查询所有额度记录
        List<AiQuota> allQuotas = quotaRepository.findAll();

        // 建立 userId → quota 映射
        Map<Long, AiQuota> userQuotaMap = new HashMap<>();
        for (AiQuota q : allQuotas) {
            Long uid = q.getUser().getId();
            // 如果同一用户有多条额度，取最新的
            if (!userQuotaMap.containsKey(uid) ||
                q.getCreateTime().isAfter(userQuotaMap.get(uid).getCreateTime())) {
                userQuotaMap.put(uid, q);
            }
        }

        // 为每个启用用户生成记录
        for (User u : enabledUsers) {
            AiQuota q = userQuotaMap.get(u.getId());
            if (q != null) {
                result.add(quotaToMap(q));
            } else {
                // 用户无额度记录，自动创建默认额度
                AiQuota defaultQuota = new AiQuota();
                defaultQuota.setUser(u);
                defaultQuota.setTokenLimit(1_000_000L);
                defaultQuota.setTokenUsed(0L);
                defaultQuota.setTimeWindowSeconds(18000);
                defaultQuota.setWindowStart(LocalDateTime.now());
                defaultQuota.setStatus(true);
                defaultQuota = quotaRepository.save(defaultQuota);
                result.add(quotaToMap(defaultQuota));
            }
        }
        return ApiResponse.success(result);
    }

    private Map<String, Object> quotaToMap(AiQuota q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", q.getId());
        m.put("user", Map.of("id", q.getUser().getId(),
                              "username", q.getUser().getUsername(),
                              "email", q.getUser().getEmail() != null ? q.getUser().getEmail() : ""));
        m.put("tokenLimit", q.getTokenLimit());
        m.put("tokenUsed", q.getTokenUsed());
        m.put("timeWindowSeconds", q.getTimeWindowSeconds());
        m.put("windowStart", q.getWindowStart());
        m.put("status", q.getStatus());
        m.put("createTime", q.getCreateTime());
        return m;
    }

    @PostMapping("/quotas")
    public ApiResponse<AiQuota> createQuota(@RequestBody AiQuotaDTO dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        AiQuota quota = new AiQuota();
        quota.setUser(user);
        if (dto.getSubscriptionId() != null) {
            quota.setSubscription(subscriptionRepository.findById(dto.getSubscriptionId())
                    .orElseThrow(() -> new RuntimeException("订阅不存在")));
        }
        quota.setTokenLimit(dto.getTokenLimit());
        quota.setTokenUsed(dto.getTokenUsed());
        quota.setTimeWindowSeconds(dto.getTimeWindowSeconds());
        quota.setWindowStart(LocalDateTime.now());
        quota.setStatus(dto.getStatus());
        return ApiResponse.success(quotaRepository.save(quota));
    }

    @PutMapping("/quotas/{id}")
    public ApiResponse<AiQuota> updateQuota(@PathVariable Long id, @RequestBody AiQuotaDTO dto) {
        AiQuota quota = quotaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("额度不存在"));
        quota.setTokenLimit(dto.getTokenLimit());
        quota.setTimeWindowSeconds(dto.getTimeWindowSeconds());
        quota.setStatus(dto.getStatus());
        return ApiResponse.success(quotaRepository.save(quota));
    }

    @DeleteMapping("/quotas/{id}")
    public ApiResponse<Void> deleteQuota(@PathVariable Long id) {
        quotaRepository.deleteById(id);
        return ApiResponse.success();
    }

    // ==================== API Key 管理 ====================

    @GetMapping("/api-keys")
    public ApiResponse<List<Map<String, Object>>> listApiKeys(@RequestParam(required = false) Long userId) {
        List<AiApiKey> keys;
        if (userId != null) {
            keys = apiKeyRepository.findByUserIdAndStatusTrue(userId);
        } else {
            keys = apiKeyRepository.findAll();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime todayEnd = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        for (AiApiKey key : keys) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", key.getId());
            m.put("user", Map.of("id", key.getUser().getId(), "username", key.getUser().getUsername(), "email", key.getUser().getEmail() != null ? key.getUser().getEmail() : ""));
            m.put("apiKey", key.getApiKey());
            m.put("keyName", key.getKeyName());
            m.put("lastUsed", key.getLastUsed());
            m.put("status", key.getStatus());
            m.put("createTime", key.getCreateTime());
            m.put("totalTokensUsed", usageLogRepository.sumTokensByUserIdAndTimeBetween(key.getUser().getId(), todayStart, todayEnd));
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/api-keys")
    public ApiResponse<Map<String, Object>> createApiKey(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        String keyName = (String) body.getOrDefault("keyName", "Default");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        String apiKey = "sk-" + generateRandomKey(48);

        AiApiKey key = new AiApiKey();
        key.setUser(user);
        key.setApiKey(apiKey);
        key.setKeyName(keyName);
        apiKeyRepository.save(key);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", key.getId());
        result.put("apiKey", apiKey);
        result.put("keyName", key.getKeyName());
        return ApiResponse.success(result);
    }

    @DeleteMapping("/api-keys/{id}")
    public ApiResponse<Void> deleteApiKey(@PathVariable Long id) {
        apiKeyRepository.deleteById(id);
        return ApiResponse.success();
    }

    // ==================== 用户列表（供下拉选择） ====================

    @GetMapping("/users")
    @Operation(summary = "获取用户列表（供下拉选择）", description = "返回所有启用用户的 id 和 username")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ai-service:view')")
    public ApiResponse<List<Map<String, Object>>> listUsersForSelect() {
        List<User> users = userRepository.findByEnabledTrue();
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("email", u.getEmail());
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    // ==================== 统计 ====================

    @Operation(summary = "AI服务统计", description = "获取AI服务统计数据（模型数、订阅数等）")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ai-service:view')")
    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getStatistics() {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime todayEnd = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalModels", modelRepository.countByStatusTrue());
        stats.put("totalSubscriptions", subscriptionRepository.count());
        stats.put("totalApiKeys", apiKeyRepository.count());
        stats.put("todayCalls", usageLogRepository.countByCreateTimeBetween(todayStart, todayEnd));
        stats.put("totalCalls", usageLogRepository.count());
        return ApiResponse.success(stats);
    }

    @GetMapping("/usage-logs")
    public ApiResponse<Map<String, Object>> getUsageLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long modelId) {

        Page<AiUsageLog> logs;
        if (userId != null && modelId != null) {
            logs = usageLogRepository.findByUserIdAndModelIdOrderByCreateTimeDesc(userId, modelId,
                    PageRequest.of(page - 1, size, Sort.by("createTime").descending()));
        } else if (userId != null) {
            logs = usageLogRepository.findByUserIdOrderByCreateTimeDesc(userId,
                    PageRequest.of(page - 1, size, Sort.by("createTime").descending()));
        } else {
            logs = usageLogRepository.findAll(
                    PageRequest.of(page - 1, size, Sort.by("createTime").descending()));
        }

        List<Map<String, Object>> dtoList = new ArrayList<>();
        for (AiUsageLog log : logs.getContent()) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", log.getId());
            dto.put("user", Map.of("id", log.getUser().getId(), "username", log.getUser().getUsername()));
            dto.put("provider", log.getProvider() != null ? Map.of("id", log.getProvider().getId(), "name", log.getProvider().getName()) : null);
            dto.put("model", log.getModel() != null ? Map.of("id", log.getModel().getId(), "modelName", log.getModel().getModelName(), "displayName", log.getModel().getDisplayName()) : null);
            dto.put("promptTokens", log.getPromptTokens());
            dto.put("completionTokens", log.getCompletionTokens());
            dto.put("totalTokens", log.getTotalTokens());
            dto.put("cost", log.getCost());
            dto.put("latencyMs", log.getLatencyMs());
            dto.put("fallbackFrom", log.getFallbackFrom());
            dto.put("statusCode", log.getStatusCode());
            dto.put("createTime", log.getCreateTime());
            dtoList.add(dto);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", logs.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        result.put("list", dtoList);
        return ApiResponse.success(result);
    }

    @GetMapping("/fallback-logs")
    public ApiResponse<Map<String, Object>> getFallbackLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AiUsageLog> logs = usageLogRepository.findAll(
                PageRequest.of(page - 1, size, Sort.by("createTime").descending()));
        List<AiUsageLog> fallbackLogs = logs.getContent().stream()
                .filter(l -> l.getFallbackFrom() != null)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", fallbackLogs.size());
        result.put("page", page);
        result.put("size", size);
        result.put("list", fallbackLogs);
        return ApiResponse.success(result);
    }

    private String generateRandomKey(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
