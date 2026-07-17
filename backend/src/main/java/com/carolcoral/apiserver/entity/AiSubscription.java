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
 * AI 用户订阅实体类
 *
 * @author carolcoral
 */
@Schema(description = "AI 用户订阅实体")
@Entity
@Table(name = "t_ai_subscription")
public class AiSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private AiProvider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", nullable = false)
    private AiModel model;

    @Column(nullable = false)
    private Integer priority = 0;

    @Column(nullable = false)
    private Integer weight = 1;

    @Column(length = 200)
    private String tags;

    @Column(nullable = false)
    private Boolean fallbackEnabled = true;

    private Integer maxTokensPerRequest;

    @Column(nullable = false)
    private Boolean status = true;

    private LocalDateTime expireTime;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;

    public AiSubscription() {}

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public AiProvider getProvider() { return provider; }
    public void setProvider(AiProvider provider) { this.provider = provider; }
    public AiModel getModel() { return model; }
    public void setModel(AiModel model) { this.model = model; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public Boolean getFallbackEnabled() { return fallbackEnabled; }
    public void setFallbackEnabled(Boolean fallbackEnabled) { this.fallbackEnabled = fallbackEnabled; }
    public Integer getMaxTokensPerRequest() { return maxTokensPerRequest; }
    public void setMaxTokensPerRequest(Integer maxTokensPerRequest) { this.maxTokensPerRequest = maxTokensPerRequest; }
    public Boolean getStatus() { return status; }
    public void setStatus(Boolean status) { this.status = status; }
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
