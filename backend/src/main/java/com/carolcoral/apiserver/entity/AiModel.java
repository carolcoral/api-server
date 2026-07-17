/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * AI 模型实体类
 *
 * @author carolcoral
 */
@Schema(description = "AI 模型实体")
@Entity
@Table(name = "t_ai_model")
public class AiModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private AiProvider provider;

    @Column(nullable = false, length = 100)
    private String modelName;

    @Column(length = 100)
    private String displayName;

    private Double inputPrice;

    private Double outputPrice;

    private Integer maxTokens;

    @Column(nullable = false)
    private Boolean supportsStream = true;

    @Column(length = 20)
    private String healthStatus = "online";

    private LocalDateTime lastHealthCheck;

    private LocalDateTime cooldownUntil;

    @Column(nullable = false)
    private Integer consecutiveFailures = 0;

    private Long avgLatencyMs;

    @Column(nullable = false)
    private Boolean status = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;

    public AiModel() {}

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AiProvider getProvider() { return provider; }
    public void setProvider(AiProvider provider) { this.provider = provider; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Double getInputPrice() { return inputPrice; }
    public void setInputPrice(Double inputPrice) { this.inputPrice = inputPrice; }
    public Double getOutputPrice() { return outputPrice; }
    public void setOutputPrice(Double outputPrice) { this.outputPrice = outputPrice; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Boolean getSupportsStream() { return supportsStream; }
    public void setSupportsStream(Boolean supportsStream) { this.supportsStream = supportsStream; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
    public LocalDateTime getLastHealthCheck() { return lastHealthCheck; }
    public void setLastHealthCheck(LocalDateTime lastHealthCheck) { this.lastHealthCheck = lastHealthCheck; }
    public LocalDateTime getCooldownUntil() { return cooldownUntil; }
    public void setCooldownUntil(LocalDateTime cooldownUntil) { this.cooldownUntil = cooldownUntil; }
    public Integer getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(Integer consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
    public Long getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(Long avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }
    public Boolean getStatus() { return status; }
    public void setStatus(Boolean status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
