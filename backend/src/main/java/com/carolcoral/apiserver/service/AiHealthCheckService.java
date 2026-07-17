/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.entity.AiModel;
import com.carolcoral.apiserver.entity.AiModelHealth;
import com.carolcoral.apiserver.entity.AiProvider;
import com.carolcoral.apiserver.repository.AiModelHealthRepository;
import com.carolcoral.apiserver.repository.AiModelRepository;
import com.carolcoral.apiserver.repository.AiProviderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AI 模型健康检查服务
 * 每 60 秒对启用状态的模型进行探测
 *
 * @author carolcoral
 */
@Service
public class AiHealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(AiHealthCheckService.class);

    private final AiModelRepository modelRepository;
    private final AiModelHealthRepository healthRepository;
    private final AiProviderRepository providerRepository;
    private final ObjectMapper objectMapper;

    private static final int OFFLINE_THRESHOLD = 5;
    private static final int COOLDOWN_MINUTES = 5;
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 15000;

    public AiHealthCheckService(AiModelRepository modelRepository,
                                 AiModelHealthRepository healthRepository,
                                 AiProviderRepository providerRepository,
                                 ObjectMapper objectMapper) {
        this.modelRepository = modelRepository;
        this.healthRepository = healthRepository;
        this.providerRepository = providerRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 定时健康检查（每 60 秒）
     */
    @Scheduled(fixedRate = 60000)
    public void scheduledHealthCheck() {
        log.debug("开始 AI 模型健康检查...");
        List<AiModel> models = modelRepository.findByStatusTrue();
        for (AiModel model : models) {
            // 跳过冷却期内的模型
            if (model.getCooldownUntil() != null
                    && LocalDateTime.now().isBefore(model.getCooldownUntil())) {
                continue;
            }
            try {
                checkModel(model);
            } catch (Exception e) {
                log.warn("健康检查失败: model={}, error={}", model.getModelName(), e.getMessage());
            }
        }
        log.debug("AI 模型健康检查完成，共检查 {} 个模型", models.size());
    }

    /**
     * 手动触发健康检查
     */
    public AiModelHealth checkModel(AiModel model) {
        AiProvider provider = model.getProvider();
        long startTime = System.currentTimeMillis();

        AiModelHealth health = new AiModelHealth();
        health.setModel(model);
        health.setCheckTime(LocalDateTime.now());

        try {
            String apiUrl = buildHealthCheckUrl(provider.getBaseUrl());
            HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            if (provider.getApiKey() != null) {
                String authHeader = "bearer".equalsIgnoreCase(provider.getAuthType())
                        ? "Bearer " + provider.getApiKey() : provider.getApiKey();
                connection.setRequestProperty("Authorization", authHeader);
            }

            // 发送最小探测请求
            Map<String, Object> probeBody = new LinkedHashMap<>();
            probeBody.put("model", model.getModelName());
            probeBody.put("messages", List.of(Map.of("role", "user", "content", "ping")));
            probeBody.put("max_tokens", 1);

            String json = objectMapper.writeValueAsString(probeBody);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int statusCode = connection.getResponseCode();
            long latency = System.currentTimeMillis() - startTime;
            connection.disconnect();

            if (statusCode >= 200 && statusCode < 500) {
                health.setStatus("success");
                health.setLatencyMs(latency);

                // 恢复健康状态
                model.setConsecutiveFailures(0);
                model.setHealthStatus("online");
                model.setCooldownUntil(null);
                if (model.getAvgLatencyMs() == null) {
                    model.setAvgLatencyMs(latency);
                } else {
                    model.setAvgLatencyMs((model.getAvgLatencyMs() * 9 + latency) / 10);
                }
            } else {
                health.setStatus("error");
                health.setErrorMsg("HTTP " + statusCode);
                handleCheckFailure(model);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            health.setStatus("timeout");
            health.setLatencyMs(latency);
            health.setErrorMsg(e.getMessage());
            handleCheckFailure(model);
        }

        model.setLastHealthCheck(LocalDateTime.now());
        modelRepository.save(model);
        healthRepository.save(health);

        return health;
    }

    private void handleCheckFailure(AiModel model) {
        int failures = model.getConsecutiveFailures() + 1;
        model.setConsecutiveFailures(failures);
        model.setHealthStatus("degraded");
        if (failures >= OFFLINE_THRESHOLD) {
            model.setHealthStatus("offline");
            model.setCooldownUntil(LocalDateTime.now().plusMinutes(COOLDOWN_MINUTES));
            log.warn("模型 {} 健康检查连续失败 {} 次，标记为 offline", model.getModelName(), failures);
        }
    }

    private String buildHealthCheckUrl(String baseUrl) {
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (!url.endsWith("/v1")) url += "/v1";
        return url + "/chat/completions";
    }
}
