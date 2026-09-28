/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * AI 模型实体类
 * <p>由 {@code AiConfigService} 依据系统设置中的 AI 配置自动同步，用于 AI 代理的模型路由。
 * 原本独立维护在「AI 服务管理」页面中的定价、健康检查等字段随该页面一并下线。</p>
 *
 * @author carolcoral
 */
@Schema(description = "AI 模型实体")
@Entity
@Table(name = "t_ai_model", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ai_model_provider_name", columnNames = {"provider_id", "model_name"})
})
public class AiModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private AiProvider provider;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "supports_stream", nullable = false)
    private Boolean supportsStream = true;

    /** 连续失败次数（运行期统计，用于熔断） */
    @Column(name = "consecutive_failures", nullable = false)
    private Integer consecutiveFailures = 0;

    /** 熔断冷却截止时间 */
    @Column(name = "cooldown_until")
    private LocalDateTime cooldownUntil;

    @Column(nullable = false)
    private Boolean status = true;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    public AiModel() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AiProvider getProvider() { return provider; }
    public void setProvider(AiProvider provider) { this.provider = provider; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Boolean getSupportsStream() { return supportsStream; }
    public void setSupportsStream(Boolean supportsStream) { this.supportsStream = supportsStream; }

    public Integer getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(Integer consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }

    public LocalDateTime getCooldownUntil() { return cooldownUntil; }
    public void setCooldownUntil(LocalDateTime cooldownUntil) { this.cooldownUntil = cooldownUntil; }

    public Boolean getStatus() { return status; }
    public void setStatus(Boolean status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
