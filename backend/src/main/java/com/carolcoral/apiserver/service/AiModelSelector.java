/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.entity.AiModel;
import com.carolcoral.apiserver.repository.AiModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 模型自动选择器
 * 负责 auto 模式和 fallback 场景下的模型选择（基于全局启用模型，不再依赖用户订阅）
 *
 * @author carolcoral
 */
@Service
public class AiModelSelector {

    private static final Logger log = LoggerFactory.getLogger(AiModelSelector.class);

    private final AiModelRepository modelRepository;

    /** 最大 fallback 重试次数 */
    private static final int MAX_FALLBACK_RETRIES = 3;

    /** 连续失败多少次后标记 offline */
    private static final int OFFLINE_THRESHOLD = 5;

    /** 冷却时间（分钟） */
    private static final int COOLDOWN_MINUTES = 5;

    public AiModelSelector(AiModelRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    /**
     * 获取所有启用且可用的模型（用于内部路由）
     */
    public List<AiModel> getAllEnabledModels() {
        return modelRepository.findByStatusTrueWithProvider().stream()
                .filter(this::isModelAvailable)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有启用状态的模型（不检查健康状态，用于内部路由，JOIN FETCH 避免 LAZY 问题）
     */
    public List<AiModel> getAllEnabledModelsIgnoreHealth() {
        return modelRepository.findByStatusTrueWithProvider();
    }

    /**
     * 自动选择模型（auto 模式）
     * <p>模型随 AI 配置自动同步，不再维护人工优先级、单价与延迟等排序依据，
     * 因此统一按模型名做稳定排序：priority / cost_first / performance_first 语义等价，
     * random 在候选集内随机打散。</p>
     *
     * @param fallbackStrategy 策略：priority/random/cost_first/performance_first
     * @return 排序后的候选模型列表（排第一的为推荐模型）
     */
    public List<AiModel> selectModels(String fallbackStrategy) {
        List<AiModel> candidates = modelRepository.findByStatusTrueWithProvider().stream()
                .filter(this::isModelAvailable)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            log.warn("没有可用的 AI 模型");
            return Collections.emptyList();
        }

        String strategy = fallbackStrategy != null ? fallbackStrategy : "priority";
        if ("random".equals(strategy)) {
            Collections.shuffle(candidates);
        } else {
            // 稳定排序，保证同一配置下选择结果可预期
            candidates.sort(Comparator.comparing(
                    m -> m.getModelName() == null ? "" : m.getModelName()));
        }
        return candidates;
    }

    /**
     * 根据模型名查找启用模型（使用 JOIN FETCH 加载 provider）
     */
    public Optional<AiModel> findModel(String modelName) {
        return modelRepository.findByStatusTrueWithProvider().stream()
                .filter(m -> m.getModelName().equals(modelName))
                .findFirst();
    }

    /**
     * 判断模型是否可用
     */
    public boolean isModelAvailable(AiModel model) {
        if (model == null || !model.getStatus()) return false;
        if (model.getCooldownUntil() != null
                && LocalDateTime.now().isBefore(model.getCooldownUntil())) {
            return false;
        }
        return true;
    }

    /**
     * 获取 fallback 候选列表（排除已尝试的模型）
     */
    public List<AiModel> getFallbackCandidates(String strategy, Set<Long> triedModelIds) {
        List<AiModel> all = selectModels(strategy);
        return all.stream()
                .filter(m -> !triedModelIds.contains(m.getId()))
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
        int failures = (model.getConsecutiveFailures() == null ? 0 : model.getConsecutiveFailures()) + 1;
        model.setConsecutiveFailures(failures);
        if (failures >= OFFLINE_THRESHOLD) {
            // 连续失败达到阈值：进入冷却期，冷却期间不再被选中
            model.setCooldownUntil(LocalDateTime.now().plusMinutes(COOLDOWN_MINUTES));
            log.warn("模型 {} 连续失败 {} 次，进入冷却 {} 分钟",
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
        model.setCooldownUntil(null);
        modelRepository.save(model);
    }

    public int getMaxFallbackRetries() {
        return MAX_FALLBACK_RETRIES;
    }
}
