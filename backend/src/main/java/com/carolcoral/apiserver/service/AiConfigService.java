/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.entity.AiConfig;
import com.carolcoral.apiserver.entity.AiModel;
import com.carolcoral.apiserver.entity.AiProvider;
import com.carolcoral.apiserver.repository.AiConfigRepository;
import com.carolcoral.apiserver.repository.AiModelRepository;
import com.carolcoral.apiserver.repository.AiProviderRepository;
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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiConfigService.class);

    @Autowired
    private AiConfigRepository aiConfigRepository;

    @Autowired
    private AiProviderRepository providerRepository;

    @Autowired
    private AiModelRepository modelRepository;

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

    @Transactional
    public AiConfig saveConfig(AiConfig config) {
        // 标准化 API 地址，避免包含非标准路径后缀
        if (config.getApiUrl() != null) {
            config.setApiUrl(normalizeApiUrl(config.getApiUrl()));
        }
        // 有 id 按 id 更新，否则新建（不再按 provider 查找，避免同一 provider 多条配置被覆盖）
        AiConfig saved;
        if (config.getId() != null) {
            AiConfig existing = aiConfigRepository.findById(config.getId()).orElse(null);
            if (existing != null) {
                existing.setProvider(config.getProvider());
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
                saved = aiConfigRepository.save(existing);
            } else {
                saved = aiConfigRepository.save(config);
            }
        } else {
            saved = aiConfigRepository.save(config);
        }
        ensureDefaultConfig();

        // 同步到 AI 服务管理表，确保 OpenAI 兼容接口和 auto 模式有具体模型可用
        syncToAiProviderAndModels(saved);

        return saved;
    }

    /**
     * 将 AI 配置同步到 AiProvider/AiModel，使外部订阅和 auto 模式能选择到具体模型
     */
    private void syncToAiProviderAndModels(AiConfig config) {
        if (!Boolean.TRUE.equals(config.getEnabled()) || config.getProvider() == null || config.getProvider().isBlank()) {
            return;
        }

        String code = config.getProvider();

        // 查找或创建服务商
        AiProvider provider = providerRepository.findByCode(code).orElse(null);
        if (provider == null) {
            provider = new AiProvider();
            provider.setCode(code);
            provider.setName(config.getProviderName() != null ? config.getProviderName() : code);
            provider.setBaseUrl(config.getApiUrl());
            provider.setApiType(code);
            provider.setAuthType("bearer");
            provider.setApiKey(config.getApiKey());
            provider.setStatus(true);
        } else {
            provider.setName(config.getProviderName() != null ? config.getProviderName() : provider.getName());
            provider.setBaseUrl(config.getApiUrl());
            provider.setApiKey(config.getApiKey());
            provider.setStatus(true);
        }
        provider = providerRepository.save(provider);

        // 解析模型列表
        List<String> modelNames = new ArrayList<>();
        if (config.getDefaultModel() != null && !config.getDefaultModel().isBlank()) {
            modelNames.add(config.getDefaultModel().trim());
        }
        if (config.getModels() != null && !config.getModels().isBlank()) {
            for (String m : config.getModels().split(",")) {
                String trimmed = m.trim();
                if (!trimmed.isEmpty() && !modelNames.contains(trimmed)) {
                    modelNames.add(trimmed);
                }
            }
        }
        // 未指定模型时，使用预设默认值
        if (modelNames.isEmpty()) {
            Map<String, Map<String, String>> presets = getPresetProviders();
            Map<String, String> preset = presets.get(code);
            if (preset != null) {
                String defaultModel = preset.get("defaultModel");
                if (defaultModel != null && !defaultModel.isBlank()) {
                    modelNames.add(defaultModel);
                }
            }
        }

        // 创建不存在的具体模型（非 auto 模式）
        for (String modelName : modelNames) {
            if (modelRepository.findByProviderIdAndModelName(provider.getId(), modelName).isEmpty()) {
                AiModel model = new AiModel();
                model.setProvider(provider);
                model.setModelName(modelName);
                model.setDisplayName(modelName);
                model.setSupportsStream(true);
                model.setStatus(true);
                model.setAutoMode(false);
                modelRepository.save(model);
                log.info("从 AI 配置同步创建模型: provider={}, model={}", provider.getName(), modelName);
            }
        }
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
        AiConfig saved = aiConfigRepository.save(config);
        ensureDefaultConfig();

        // 同步服务商启用状态
        if (saved.getProvider() != null) {
            providerRepository.findByCode(saved.getProvider()).ifPresent(provider -> {
                provider.setStatus(enabled);
                providerRepository.save(provider);
            });
        }

        return saved;
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
        ensureDefaultConfig();
    }

    /**
     * 确保至少存在一个默认配置：
     * - 如果只有一个启用配置，自动设为默认
     * - 如果多个启用配置且没有默认配置，自动将第一个设为默认
     */
    private void ensureDefaultConfig() {
        List<AiConfig> enabledConfigs = aiConfigRepository.findAllByEnabledTrue();
        if (enabledConfigs.isEmpty()) {
            return;
        }
        boolean hasDefault = enabledConfigs.stream()
                .anyMatch(c -> Boolean.TRUE.equals(c.getIsDefault()));
        if (hasDefault) {
            return;
        }
        AiConfig first = enabledConfigs.get(0);
        // 取消其他配置的默认标记（理论上没有，但防御性处理）
        for (AiConfig c : aiConfigRepository.findAll()) {
            if (Boolean.TRUE.equals(c.getIsDefault()) && !c.getId().equals(first.getId())) {
                c.setIsDefault(false);
                aiConfigRepository.save(c);
            }
        }
        first.setIsDefault(true);
        aiConfigRepository.save(first);
        log.info("自动设置默认 AI 配置: id={}, providerName={}", first.getId(), first.getProviderName());
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
