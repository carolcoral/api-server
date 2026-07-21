/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.entity.AiModel;
import com.carolcoral.apiserver.entity.AiProvider;
import com.carolcoral.apiserver.entity.AiSubscription;
import com.carolcoral.apiserver.repository.AiModelRepository;
import com.carolcoral.apiserver.repository.AiSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 模型自动选择器
 * 负责 auto 模式和 fallback 场景下的模型选择
 *
 * @author carolcoral
 */
@Service
public class AiModelSelector {

    private static final Logger log = LoggerFactory.getLogger(AiModelSelector.class);

    private final AiSubscriptionRepository subscriptionRepository;
    private final AiModelRepository modelRepository;

    /** 最大 fallback 重试次数 */
    private static final int MAX_FALLBACK_RETRIES = 3;

    /** 连续失败多少次后标记 offline */
    private static final int OFFLINE_THRESHOLD = 5;

    /** 冷却时间（分钟） */
    private static final int COOLDOWN_MINUTES = 5;

    public AiModelSelector(AiSubscriptionRepository subscriptionRepository,
                           AiModelRepository modelRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.modelRepository = modelRepository;
    }

    /**
     * 获取所有启用状态的订阅（用于内部路由）
     */
    public List<AiSubscription> getAllEnabledSubscriptions() {
        return subscriptionRepository.findByStatusTrue().stream()
                .filter(sub -> isModelAvailable(sub.getModel()))
                .collect(Collectors.toList());
    }

    /**
     * 获取所有启用状态的订阅（不检查健康状态，用于内部路由，JOIN FETCH 避免 LAZY 问题）
     */
    public List<AiSubscription> getAllEnabledSubscriptionsIgnoreHealth() {
        return subscriptionRepository.findByStatusTrueWithModelAndProvider();
    }

    /**
     * 自动选择模型（auto 模式）
     *
     * @param userId          用户ID
     * @param fallbackStrategy 策略：priority/random/cost_first/performance_first
     * @return 排序后的候选模型列表（排第一的为推荐模型）
     */
    public List<AiSubscription> selectModels(Long userId, String fallbackStrategy) {
        // 检查用户是否订阅了自动模式（使用 JOIN FETCH 加载 model 和 provider）
        List<AiSubscription> userSubs = subscriptionRepository.findByUserIdAndStatusTrueWithModelAndProvider(userId);
        boolean hasAutoMode = userSubs.stream()
                .anyMatch(sub -> sub.getModel().getAutoMode() != null && sub.getModel().getAutoMode());

        List<AiSubscription> subscriptions;

        if (hasAutoMode) {
            // 自动模式：从所有可用模型中选择（全局）
            List<AiModel> allModels = modelRepository.findByStatusTrueWithProvider();
            subscriptions = allModels.stream()
                    .filter(this::isModelAvailable)
                    .filter(m -> m.getAutoMode() == null || !m.getAutoMode())
                    .map(m -> {
                        AiSubscription virtualSub = new AiSubscription();
                        virtualSub.setModel(m);
                        virtualSub.setProvider(m.getProvider());
                        virtualSub.setPriority(0);
                        virtualSub.setWeight(1);
                        virtualSub.setFallbackEnabled(true);
                        return virtualSub;
                    })
                    .collect(Collectors.toList());
        } else {
            // 普通模式：从用户订阅中选择（使用 JOIN FETCH 加载 model 和 provider）
            subscriptions = subscriptionRepository
                    .findByUserIdAndStatusTrueAndFallbackEnabledTrueWithModelAndProvider(userId);

            // 过滤不可用模型 和 autoMode 模型（自动模式本身不应作为候选）
            subscriptions = subscriptions.stream()
                    .filter(sub -> isModelAvailable(sub.getModel()))
                    .filter(sub -> sub.getModel().getAutoMode() == null || !sub.getModel().getAutoMode())
                    .collect(Collectors.toList());
        }

        if (subscriptions.isEmpty()) {
            log.warn("用户 {} 没有可用的 AI 模型", userId);
            return Collections.emptyList();
        }

        // 按策略排序
        String strategy = fallbackStrategy != null ? fallbackStrategy : "priority";
        switch (strategy) {
            case "random":
                Collections.shuffle(subscriptions);
                break;
            case "cost_first":
                subscriptions.sort(Comparator.comparing(
                        sub -> sub.getModel().getInputPrice() != null ? sub.getModel().getInputPrice() : Double.MAX_VALUE));
                break;
            case "performance_first":
                subscriptions.sort(Comparator.comparing(
                        sub -> sub.getModel().getAvgLatencyMs() != null ? sub.getModel().getAvgLatencyMs() : Long.MAX_VALUE));
                break;
            case "priority":
            default:
                // 普通模式下按优先级排序，自动模式下所有虚拟订阅优先级相同
                subscriptions.sort(Comparator.comparingInt(AiSubscription::getPriority));
                break;
        }

        return subscriptions;
    }

    /**
     * 根据模型名查找订阅（使用 JOIN FETCH 加载 model 和 provider）
     */
    public Optional<AiSubscription> findSubscription(Long userId, String modelName) {
        List<AiSubscription> subs = subscriptionRepository
                .findByUserIdAndStatusTrueWithModelAndProvider(userId);
        return subs.stream()
                .filter(sub -> sub.getModel().getModelName().equals(modelName)
                        && sub.getModel().getStatus())
                .findFirst();
    }

    /**
     * 判断模型是否可用
     */
    public boolean isModelAvailable(AiModel model) {
        if (model == null || !model.getStatus()) return false;
        if ("offline".equals(model.getHealthStatus())) return false;
        if (model.getCooldownUntil() != null
                && LocalDateTime.now().isBefore(model.getCooldownUntil())) {
            return false;
        }
        return true;
    }

    /**
     * 获取 fallback 候选列表（排除已尝试的模型）
     */
    public List<AiSubscription> getFallbackCandidates(Long userId, String strategy,
                                                       Set<Long> triedModelIds) {
        List<AiSubscription> all = selectModels(userId, strategy);
        return all.stream()
                .filter(sub -> !triedModelIds.contains(sub.getModel().getId()))
                .limit(MAX_FALLBACK_RETRIES)
                .collect(Collectors.toList());
    }

    /**
     * 标记模型调用失败（同步版本，兼容旧代码）
     */
    public void markModelFailure(AiModel model) {
        markModelFailureAsync(model.getId());
    }

    /**
     * 标记模型调用成功（同步版本，兼容旧代码）
     */
    public void markModelSuccess(AiModel model, long latencyMs) {
        markModelSuccessAsync(model.getId(), latencyMs);
    }

    /**
     * 异步标记模型调用失败，避免流式请求事务中写库导致 SQLITE_BUSY
     */
    @Async("taskExecutor")
    public void markModelFailureAsync(Long modelId) {
        AiModel model = modelRepository.findById(modelId).orElse(null);
        if (model == null) return;
        int failures = model.getConsecutiveFailures() + 1;
        model.setConsecutiveFailures(failures);
        model.setHealthStatus("degraded");
        if (failures >= OFFLINE_THRESHOLD) {
            model.setHealthStatus("offline");
            model.setCooldownUntil(LocalDateTime.now().plusMinutes(COOLDOWN_MINUTES));
            log.warn("模型 {} 连续失败 {} 次，标记为 offline，冷却 {} 分钟",
                    model.getModelName(), failures, COOLDOWN_MINUTES);
        }
        modelRepository.save(model);
    }

    /**
     * 异步标记模型调用成功，避免流式请求事务中写库导致 SQLITE_BUSY
     */
    @Async("taskExecutor")
    public void markModelSuccessAsync(Long modelId, long latencyMs) {
        AiModel model = modelRepository.findById(modelId).orElse(null);
        if (model == null) return;
        model.setConsecutiveFailures(0);
        model.setHealthStatus("online");
        model.setCooldownUntil(null);
        if (model.getAvgLatencyMs() == null) {
            model.setAvgLatencyMs(latencyMs);
        } else {
            // 指数移动平均
            model.setAvgLatencyMs((model.getAvgLatencyMs() * 9 + latencyMs) / 10);
        }
        model.setLastHealthCheck(LocalDateTime.now());
        modelRepository.save(model);
    }

    public int getMaxFallbackRetries() {
        return MAX_FALLBACK_RETRIES;
    }
}
