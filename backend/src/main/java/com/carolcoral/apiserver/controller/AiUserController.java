/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.entity.*;
import com.carolcoral.apiserver.repository.*;
import com.carolcoral.apiserver.service.AiHealthCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 用户自助服务控制器
 * 普通用户通过此控制器管理自己的订阅和 API Key
 *
 * @author carolcoral
 */
@Tag(name = "AI用户自助", description = "用户管理自己的 AI 订阅、API Key")
@RestController
@RequestMapping("/api/user/ai")
public class AiUserController {

    private static final Logger log = LoggerFactory.getLogger(AiUserController.class);

    private final AiProviderRepository providerRepository;
    private final AiModelRepository modelRepository;
    private final AiSubscriptionRepository subscriptionRepository;
    private final AiApiKeyRepository apiKeyRepository;
    private final AiUsageLogRepository usageLogRepository;
    private final UserRepository userRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public AiUserController(AiProviderRepository providerRepository,
                            AiModelRepository modelRepository,
                            AiSubscriptionRepository subscriptionRepository,
                            AiApiKeyRepository apiKeyRepository,
                            AiUsageLogRepository usageLogRepository,
                            UserRepository userRepository) {
        this.providerRepository = providerRepository;
        this.modelRepository = modelRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.usageLogRepository = usageLogRepository;
        this.userRepository = userRepository;
    }

    /**
     * 获取当前登录用户
     */
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    // ==================== 可订阅的服务商和模型 ====================

    @GetMapping("/providers")
    @Operation(summary = "获取可订阅的服务商列表（仅启用状态的）")
    @PreAuthorize("hasAuthority('ai-subscription:view')")
    public ApiResponse<List<Map<String, Object>>> listAvailableProviders() {
        List<AiProvider> providers = providerRepository.findByStatusTrue();
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiProvider p : providers) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("code", p.getCode());
            m.put("description", p.getDescription());
            m.put("apiType", p.getApiType());
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/providers/{providerId}/models")
    @Operation(summary = "获取服务商下可订阅的模型列表（仅启用状态的）")
    @PreAuthorize("hasAuthority('ai-subscription:view')")
    public ApiResponse<List<Map<String, Object>>> listAvailableModels(@PathVariable Long providerId) {
        List<AiModel> models = modelRepository.findByProviderIdAndStatusTrue(providerId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiModel m : models) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("modelName", m.getModelName());
            item.put("displayName", m.getDisplayName());
            item.put("maxTokens", m.getMaxTokens());
            item.put("inputPrice", m.getInputPrice());
            item.put("outputPrice", m.getOutputPrice());
            item.put("supportsStream", m.getSupportsStream());
            item.put("healthStatus", m.getHealthStatus());
            item.put("providerName", m.getProvider().getName());
            item.put("subscriberCount", subscriptionRepository.countByModelIdAndStatusTrue(m.getId()));
            result.add(item);
        }
        return ApiResponse.success(result);
    }

    // ==================== 我的订阅 ====================

    @GetMapping("/subscriptions")
    @Operation(summary = "获取我的订阅列表")
    @PreAuthorize("hasAuthority('ai-subscription:view')")
    public ApiResponse<List<Map<String, Object>>> listMySubscriptions() {
        User user = getCurrentUser();
        List<AiSubscription> subs = subscriptionRepository.findByUserIdAndStatusTrue(user.getId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiSubscription sub : subs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", sub.getId());
            m.put("providerName", sub.getProvider().getName());
            m.put("providerCode", sub.getProvider().getCode());
            m.put("modelName", sub.getModel().getModelName());
            m.put("displayName", sub.getModel().getDisplayName());
            m.put("maxTokens", sub.getModel().getMaxTokens());
            m.put("inputPrice", sub.getModel().getInputPrice());
            m.put("outputPrice", sub.getModel().getOutputPrice());
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

    @PostMapping("/subscriptions")
    @Operation(summary = "订阅模型")
    @PreAuthorize("hasAuthority('ai-subscription:subscribe')")
    public ApiResponse<Map<String, Object>> subscribeModel(@RequestBody Map<String, Object> body) {
        User user = getCurrentUser();
        Long modelId = ((Number) body.get("modelId")).longValue();

        AiModel model = modelRepository.findById(modelId)
                .orElseThrow(() -> new RuntimeException("模型不存在"));

        if (!model.getStatus()) {
            return ApiResponse.error("该模型已被禁用");
        }

        if (subscriptionRepository.existsByUserIdAndModelId(user.getId(), modelId)) {
            return ApiResponse.error("您已订阅此模型");
        }

        AiSubscription sub = new AiSubscription();
        sub.setUser(user);
        sub.setProvider(model.getProvider());
        sub.setModel(model);
        sub.setPriority(0);
        sub.setWeight(1);
        sub.setFallbackEnabled(true);
        sub.setStatus(true);
        sub = subscriptionRepository.save(sub);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", sub.getId());
        result.put("message", "订阅成功");
        return ApiResponse.success(result);
    }

    @DeleteMapping("/subscriptions/{id}")
    @Operation(summary = "取消订阅")
    @PreAuthorize("hasAuthority('ai-subscription:subscribe')")
    public ApiResponse<Void> unsubscribeModel(@PathVariable Long id) {
        User user = getCurrentUser();
        AiSubscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订阅不存在"));

        if (!sub.getUser().getId().equals(user.getId())) {
            return ApiResponse.error("无权操作此订阅");
        }

        subscriptionRepository.deleteById(id);
        return ApiResponse.success();
    }

    // ==================== 我的 API Keys ====================

    @GetMapping("/api-keys")
    @Operation(summary = "获取我的 API Key 列表")
    @PreAuthorize("hasAuthority('ai-subscription:view')")
    public ApiResponse<List<Map<String, Object>>> listMyApiKeys() {
        User user = getCurrentUser();
        List<AiApiKey> keys = apiKeyRepository.findByUserIdAndStatusTrue(user.getId());
        List<Map<String, Object>> result = new ArrayList<>();
        java.time.LocalDateTime todayStart = java.time.LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        java.time.LocalDateTime todayEnd = java.time.LocalDateTime.now().withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        for (AiApiKey key : keys) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", key.getId());
            m.put("keyName", key.getKeyName());
            m.put("apiKey", maskApiKey(key.getApiKey()));
            m.put("lastUsed", key.getLastUsed());
            m.put("createTime", key.getCreateTime());
            m.put("totalTokensUsed", usageLogRepository.sumTokensByUserIdAndTimeBetween(user.getId(), todayStart, todayEnd));
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/api-keys")
    @Operation(summary = "创建我的 API Key")
    @PreAuthorize("hasAuthority('ai-subscription:key-manage')")
    public ApiResponse<Map<String, Object>> createMyApiKey(@RequestBody Map<String, Object> body) {
        User user = getCurrentUser();
        String keyName = (String) body.getOrDefault("keyName", "Default");

        String apiKey = "sk-" + generateRandomKey(48);

        AiApiKey key = new AiApiKey();
        key.setUser(user);
        key.setApiKey(apiKey);
        key.setKeyName(keyName);
        key = apiKeyRepository.save(key);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", key.getId());
        result.put("apiKey", apiKey);
        result.put("keyName", key.getKeyName());
        result.put("createTime", key.getCreateTime());
        return ApiResponse.success(result);
    }

    @DeleteMapping("/api-keys/{id}")
    @Operation(summary = "删除我的 API Key")
    @PreAuthorize("hasAuthority('ai-subscription:key-manage')")
    public ApiResponse<Void> deleteMyApiKey(@PathVariable Long id) {
        User user = getCurrentUser();
        AiApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API Key 不存在"));

        if (!key.getUser().getId().equals(user.getId())) {
            return ApiResponse.error("无权操作此 API Key");
        }

        apiKeyRepository.deleteById(id);
        return ApiResponse.success();
    }

    // ==================== 工具方法 ====================

    private String generateRandomKey(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() <= 8) return key;
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
