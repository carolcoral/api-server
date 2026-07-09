/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * AI 服务配置实体
 * 支持预设服务商 + 自定义 OpenAI 协议服务商
 *
 * @author carolcoral
 * @since 2026-06-23
 */
@Schema(description = "AI 服务配置实体")
@Entity
@Table(name = "t_ai_config")
public class AiConfig {

    // ==================== 预设服务商标识常量 ====================
    public static final String PROVIDER_OPENAI     = "openai";
    public static final String PROVIDER_AZURE      = "azure";
    public static final String PROVIDER_GOOGLE     = "google";
    public static final String PROVIDER_ANTHROPIC  = "anthropic";
    public static final String PROVIDER_DEEPSEEK   = "deepseek";
    public static final String PROVIDER_QWEN       = "qwen";
    public static final String PROVIDER_ZHIPU      = "zhipu";
    public static final String PROVIDER_MOONSHOT   = "moonshot";
    public static final String PROVIDER_BAICHUAN   = "baichuan";
    public static final String PROVIDER_MINIMAX    = "minimax";
    public static final String PROVIDER_XIAOMI     = "xiaomi";
    public static final String PROVIDER_BYTE_DANCE = "bytedance";
    public static final String PROVIDER_CUSTOM     = "custom";

    @Schema(description = "配置ID", example = "1")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "服务商标识", example = "openai")
    @Column(nullable = false, length = 50)
    private String provider;

    @Schema(description = "服务商显示名称", example = "OpenAI")
    @Column(nullable = false, length = 100)
    private String providerName;

    @Schema(description = "API 地址（OpenAI 协议兼容端点）", example = "https://api.openai.com")
    @Column(nullable = false, length = 500)
    private String apiUrl;

    @Schema(description = "API Key")
    @Column(nullable = false, length = 500)
    private String apiKey;

    @Schema(description = "默认模型", example = "gpt-4o")
    @Column(length = 100)
    private String defaultModel;

    @Schema(description = "最大 Token 数", example = "4096")
    @Column
    private Integer maxTokens = 4096;

    @Schema(description = "温度参数 (0-2)", example = "0.7")
    @Column
    private Double temperature = 0.7;

    @Schema(description = "AI 请求超时时间（秒），默认 120", example = "120")
    @Column
    private Integer timeout = 120;

    @Schema(description = "是否启用", example = "true")
    @Column(nullable = false)
    private Boolean enabled = false;

    @Schema(description = "是否默认 AI 设置", example = "false")
    @Column(nullable = false)
    private Boolean isDefault = false;

    @Schema(description = "支持的模型列表（逗号分隔），如 gpt-4o,gpt-4o-mini,o3-mini", example = "gpt-4o,gpt-4o-mini")
    @Column(length = 1000)
    private String models;

    @Schema(description = "创建时间")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @Column(nullable = false)
    private LocalDateTime updateTime;

    public AiConfig() {}

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createTime = now;
        this.updateTime = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }

    // ==================== Getter / Setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public String getModels() { return models; }
    public void setModels(String models) { this.models = models; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AiConfig that = (AiConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
