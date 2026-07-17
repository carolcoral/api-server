/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.entity.AiQuota;
import com.carolcoral.apiserver.repository.AiQuotaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 额度管理服务
 *
 * @author carolcoral
 */
@Service
public class AiQuotaService {

    private static final Logger log = LoggerFactory.getLogger(AiQuotaService.class);

    private final AiQuotaRepository aiQuotaRepository;

    public AiQuotaService(AiQuotaRepository aiQuotaRepository) {
        this.aiQuotaRepository = aiQuotaRepository;
    }

    /**
     * 检查用户是否有可用额度
     *
     * @param userId 用户ID
     * @return true=有额度 false=额度不足
     */
    public boolean hasQuota(Long userId) {
        List<AiQuota> quotas = aiQuotaRepository.findByUserIdAndStatusTrue(userId);
        if (quotas.isEmpty()) {
            return true; // 没有配置额度限制，默认允许
        }
        for (AiQuota quota : quotas) {
            if (isQuotaValid(quota)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 扣减 token
     *
     * @param userId 用户ID
     * @param tokens 消耗的 token 数
     */
    @Transactional
    public void deductTokens(Long userId, int tokens) {
        List<AiQuota> quotas = aiQuotaRepository.findByUserIdAndStatusTrue(userId);
        int remaining = tokens;
        for (AiQuota quota : quotas) {
            if (!isQuotaValid(quota)) continue;
            long available = quota.getTokenLimit() - quota.getTokenUsed();
            if (available <= 0) continue;
            int deduct = (int) Math.min(remaining, available);
            quota.setTokenUsed(quota.getTokenUsed() + deduct);
            aiQuotaRepository.save(quota);
            remaining -= deduct;
            if (remaining <= 0) break;
        }
    }

    /**
     * 重置过期时间窗口
     */
    @Transactional
    public void resetExpiredWindows() {
        List<AiQuota> allQuotas = aiQuotaRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        for (AiQuota quota : allQuotas) {
            if (quota.getWindowStart() != null && quota.getTimeWindowSeconds() != null) {
                LocalDateTime windowEnd = quota.getWindowStart().plusSeconds(quota.getTimeWindowSeconds());
                if (now.isAfter(windowEnd)) {
                    quota.setTokenUsed(0L);
                    quota.setWindowStart(now);
                    aiQuotaRepository.save(quota);
                    log.debug("重置额度窗口: userId={}, quotaId={}", quota.getUser().getId(), quota.getId());
                }
            }
        }
    }

    private boolean isQuotaValid(AiQuota quota) {
        if (quota.getTokenLimit() == null || quota.getTokenLimit() <= 0) return false;
        if (quota.getTokenUsed() >= quota.getTokenLimit()) {
            // 检查时间窗口是否过期
            if (quota.getWindowStart() != null && quota.getTimeWindowSeconds() != null) {
                LocalDateTime windowEnd = quota.getWindowStart().plusSeconds(quota.getTimeWindowSeconds());
                if (LocalDateTime.now().isAfter(windowEnd)) {
                    return true; // 窗口已过期，下次请求时会重置
                }
            }
            return false;
        }
        return true;
    }
}
