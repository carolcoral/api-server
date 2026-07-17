/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ChatCompletionResponse;
import com.carolcoral.apiserver.entity.*;
import com.carolcoral.apiserver.repository.AiUsageLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 调用日志与统计服务
 *
 * @author carolcoral
 */
@Service
public class AiUsageService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

    private final AiUsageLogRepository usageLogRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_BODY_SIZE = 5000;

    public AiUsageService(AiUsageLogRepository usageLogRepository, ObjectMapper objectMapper) {
        this.usageLogRepository = usageLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 记录成功调用
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(User user, AiProvider provider, AiModel model,
                           String requestBody, ChatCompletionResponse response,
                           long latencyMs, Long fallbackFrom) {
        try {
            AiUsageLog logEntry = new AiUsageLog();
            logEntry.setUser(user);
            logEntry.setProvider(provider);
            logEntry.setModel(model);
            logEntry.setRequestBody(truncate(requestBody));
            logEntry.setResponseBody(truncate(objectMapper.writeValueAsString(response)));
            logEntry.setStatusCode(200);
            logEntry.setLatencyMs(latencyMs);
            logEntry.setFallbackFrom(fallbackFrom);

            if (response.getUsage() != null) {
                logEntry.setPromptTokens(response.getUsage().getPromptTokens());
                logEntry.setCompletionTokens(response.getUsage().getCompletionTokens());
                logEntry.setTotalTokens(response.getUsage().getTotalTokens());

                // 计算费用
                double cost = 0;
                if (model.getInputPrice() != null) {
                    cost += model.getInputPrice() * response.getUsage().getPromptTokens() / 1000.0;
                }
                if (model.getOutputPrice() != null) {
                    cost += model.getOutputPrice() * response.getUsage().getCompletionTokens() / 1000.0;
                }
                logEntry.setCost(Math.round(cost * 1000000.0) / 1000000.0);
            }

            usageLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("记录 AI 调用日志失败: {}", e.getMessage());
        }
    }

    /**
     * 记录失败调用
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(User user, AiProvider provider, AiModel model,
                           String requestBody, int statusCode, String errorMsg,
                           long latencyMs) {
        try {
            AiUsageLog logEntry = new AiUsageLog();
            logEntry.setUser(user);
            logEntry.setProvider(provider);
            logEntry.setModel(model);
            logEntry.setRequestBody(truncate(requestBody));
            logEntry.setStatusCode(statusCode);
            logEntry.setErrorMsg(errorMsg);
            logEntry.setLatencyMs(latencyMs);
            logEntry.setTotalTokens(0);
            logEntry.setCost(0.0);

            usageLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("记录 AI 失败日志失败: {}", e.getMessage());
        }
    }

    private String truncate(String text) {
        if (text == null) return null;
        if (text.length() <= MAX_BODY_SIZE) return text;
        return text.substring(0, MAX_BODY_SIZE) + "...[truncated]";
    }
}
