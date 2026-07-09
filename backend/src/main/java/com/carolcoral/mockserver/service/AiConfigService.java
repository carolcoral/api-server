/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.service;

import com.carolcoral.mockserver.entity.AiConfig;
import com.carolcoral.mockserver.repository.AiConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.*;

/**
 * AI 配置服务
 *
 * @author carolcoral
 * @since 2026-06-23
 */
@Service
public class AiConfigService {

    @Autowired
    private AiConfigRepository aiConfigRepository;

    /**
     * 获取所有 AI 配置
     */
    public List<AiConfig> getAllConfigs() {
        return aiConfigRepository.findAll();
    }

    /**
     * 获取启用的 AI 配置（优先返回默认配置，否则返回第一个启用的）
     */
    public AiConfig getEnabledConfig() {
        return aiConfigRepository.findFirstByIsDefaultTrueAndEnabledTrue()
                .orElseGet(() -> aiConfigRepository.findFirstByEnabledTrue().orElse(null));
    }

    /**
     * 获取所有启用的 AI 配置列表
     */
    public List<AiConfig> getEnabledConfigs() {
        return aiConfigRepository.findAllByEnabledTrue();
    }

    /**
     * 按 ID 获取配置
     */
    public AiConfig getById(Long id) {
        return aiConfigRepository.findById(id).orElse(null);
    }

    /**
     * 按服务商标识获取配置
     */
    public AiConfig getByProvider(String provider) {
        return aiConfigRepository.findByProvider(provider).orElse(null);
    }

    /**
     * 保存或更新 AI 配置
     */
    @Transactional
    public AiConfig saveConfig(AiConfig config) {
        // 标准化 API 地址，避免包含非标准路径后缀
        if (config.getApiUrl() != null) {
            config.setApiUrl(normalizeApiUrl(config.getApiUrl()));
        }
        AiConfig existing = aiConfigRepository.findByProvider(config.getProvider()).orElse(null);
        if (existing != null) {
            existing.setProviderName(config.getProviderName());
            existing.setApiUrl(config.getApiUrl());
            existing.setApiKey(config.getApiKey());
            existing.setDefaultModel(config.getDefaultModel());
            existing.setMaxTokens(config.getMaxTokens());
            existing.setTemperature(config.getTemperature());
            existing.setTimeout(config.getTimeout());
            existing.setEnabled(config.getEnabled());
            existing.setModels(config.getModels());
            // 只有通过 setDefault 接口设置，保存时不自动设置
            if (Boolean.TRUE.equals(config.getIsDefault())) {
                existing.setIsDefault(true);
            }
            return aiConfigRepository.save(existing);
        }
        return aiConfigRepository.save(config);
    }

    /**
     * 切换启用状态（支持同时启用多个服务商）
     * 如果禁用的是默认配置，则清除其默认标记
     */
    @Transactional
    public AiConfig toggleEnabled(Long id, boolean enabled) {
        AiConfig config = aiConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("AI配置不存在: " + id));

        config.setEnabled(enabled);
        // 如果禁用的是默认配置，清除默认标记
        if (!enabled && Boolean.TRUE.equals(config.getIsDefault())) {
            config.setIsDefault(false);
        }
        return aiConfigRepository.save(config);
    }

    /**
     * 设置默认 AI 配置（仅一个，其他全部取消默认标记）
     */
    @Transactional
    public AiConfig setDefault(Long id) {
        AiConfig config = aiConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("AI配置不存在: " + id));

        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new RuntimeException("只能将已启用的 AI 设置设为默认");
        }

        // 取消所有其他配置的默认标记
        List<AiConfig> all = aiConfigRepository.findAll();
        for (AiConfig c : all) {
            if (!c.getId().equals(id) && Boolean.TRUE.equals(c.getIsDefault())) {
                c.setIsDefault(false);
                aiConfigRepository.save(c);
            }
        }

        config.setIsDefault(true);
        return aiConfigRepository.save(config);
    }

    /**
     * 删除 AI 配置
     */
    @Transactional
    public void deleteConfig(Long id) {
        AiConfig config = aiConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("AI配置不存在: " + id));
        aiConfigRepository.delete(config);
    }

    /**
     * 获取预设服务商列表（含默认 API 地址和模型）
     */
    public Map<String, Map<String, String>> getPresetProviders() {
        Map<String, Map<String, String>> providers = new LinkedHashMap<>();

        // 国际
        putProvider(providers, "openai",    "OpenAI",         "https://api.openai.com/v1",              "gpt-4o",                   "https://openai.com");
        putProvider(providers, "azure",     "Azure OpenAI",   "https://{resource}.openai.azure.com",    "gpt-4",                    "https://azure.microsoft.com/en-us/products/ai-services/openai-service");
        putProvider(providers, "google",    "Google Gemini",  "https://generativelanguage.googleapis.com", "gemini-2.5-flash",     "https://ai.google.dev");
        putProvider(providers, "anthropic", "Anthropic Claude","https://api.anthropic.com",             "claude-sonnet-4-20250514", "https://www.anthropic.com");

        // 中国境内主流服务商
        putProvider(providers, "deepseek",  "DeepSeek",       "https://api.deepseek.com/v1",            "deepseek-chat",            "https://www.deepseek.com");
        putProvider(providers, "qwen",      "通义千问",        "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus",   "https://tongyi.aliyun.com");
        putProvider(providers, "zhipu",     "智谱 GLM",       "https://open.bigmodel.cn/api/paas/v4",   "glm-4-plus",               "https://open.bigmodel.cn");
        putProvider(providers, "moonshot",  "Moonshot",       "https://api.moonshot.cn/v1",             "moonshot-v1-8k",           "https://www.moonshot.cn");
        putProvider(providers, "baichuan",  "百川智能",        "https://api.baichuan-ai.com/v1",         "Baichuan4",                "https://www.baichuan-ai.com");
        putProvider(providers, "minimax",   "MiniMax",        "https://api.minimax.chat/v1",            "abab6.5s-chat",            "https://www.minimaxi.com");
        putProvider(providers, "xiaomi",   "小米 MiMo",       "https://api.xiaomimimo.com/v1",           "mimo-pro",                 "https://mimo.xiaomi.com");
        putProvider(providers, "bytedance","火山引擎（豆包）",  "https://ark.cn-beijing.volces.com/api/v3", "doubao-pro-256k",       "https://www.volcengine.com/product/doubao");

        // 自定义
        putProvider(providers, "custom",    "自定义（OpenAI 协议）", "", "", "");

        return providers;
    }

    private void putProvider(Map<String, Map<String, String>> map, String key, String name, String url, String model, String website) {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("name", name);
        info.put("apiUrl", url);
        info.put("defaultModel", model);
        info.put("website", website);
        map.put(key, info);
    }

    /**
     * 标准化 API 地址：移除尾部斜杠及非标准路径后缀（/chat/completions、/responses 等），
     * 确保后续拼接 /chat/completions 时不会产生重复或错误路径。
     */
    static String normalizeApiUrl(String apiUrl) {
        if (apiUrl == null) return null;
        String url = apiUrl.trim();
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
        return url;
    }

    /**
     * 构建 chat/completions 请求 URL
     */
    static String buildChatCompletionsUrl(String apiUrl) {
        String base = normalizeApiUrl(apiUrl);
        if (base == null) return null;
        return base + "/chat/completions";
    }

    /**
     * 连通性验证 - 向 AI API 发送一个轻量请求验证配置是否正确
     *
     * @param apiUrl  API 地址
     * @param apiKey  API 密钥
     * @param model   模型名称
     * @return 验证结果：success 为 true 表示通过，否则附带错误信息
     */
    public Map<String, Object> testConnectivity(String apiUrl, String apiKey, String model) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (apiUrl == null || apiUrl.isBlank()) {
            result.put("success", false);
            result.put("error", "API 地址不能为空");
            return result;
        }
        if (apiKey == null || apiKey.isBlank()) {
            result.put("success", false);
            result.put("error", "API Key 不能为空");
            return result;
        }

        // 构建 chat completions URL（先标准化 apiUrl 再拼接）
        String chatUrl = buildChatCompletionsUrl(apiUrl);

        String testModel = (model != null && !model.isBlank()) ? model : "gpt-4o";

        // 构建最小化请求体（只请求1个 token 的回复，降低消耗）
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", testModel);
        requestBody.put("max_tokens", 1);
        requestBody.put("temperature", 0);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", "hi");
        messages.add(msg);
        requestBody.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // 配置超时（连接5s，读取15s）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        RestTemplate restTemplate = new RestTemplate(factory);

        long startTime = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restTemplate.exchange(chatUrl, HttpMethod.POST, entity, String.class);
            long elapsed = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful()) {
                result.put("success", true);
                result.put("message", "连通性验证通过");
                result.put("latency", elapsed);
                result.put("model", testModel);
            } else {
                result.put("success", false);
                result.put("error", "服务返回错误状态码: " + response.getStatusCode().value());
                result.put("detail", response.getBody());
            }
        } catch (RestClientException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            result.put("success", false);
            result.put("latency", elapsed);

            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException) {
                result.put("error", "连接超时，请检查 API 地址是否正确、网络是否可达");
            } else if (cause instanceof ConnectException) {
                result.put("error", "无法连接到服务商，请检查 API 地址和网络连接");
            } else if (e.getMessage() != null && e.getMessage().contains("401")) {
                result.put("error", "认证失败（401），请检查 API Key 是否正确");
            } else if (e.getMessage() != null && e.getMessage().contains("403")) {
                result.put("error", "访问被拒绝（403），API Key 可能没有权限");
            } else {
                result.put("error", "请求失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        }

        return result;
    }
}
