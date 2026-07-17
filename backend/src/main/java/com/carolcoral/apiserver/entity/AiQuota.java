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
 * AI 额度管理实体类
 *
 * @author carolcoral
 */
@Schema(description = "AI 额度管理实体")
@Entity
@Table(name = "t_ai_quota")
public class AiQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private AiSubscription subscription;

    @Column(nullable = false)
    private Long tokenLimit;

    @Column(nullable = false)
    private Long tokenUsed = 0L;

    @Column(nullable = false)
    private Integer timeWindowSeconds = 18000; // 默认5小时

    private LocalDateTime windowStart;

    @Column(nullable = false)
    private Boolean status = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    public AiQuota() {}

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (windowStart == null) {
            windowStart = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public AiSubscription getSubscription() { return subscription; }
    public void setSubscription(AiSubscription subscription) { this.subscription = subscription; }
    public Long getTokenLimit() { return tokenLimit; }
    public void setTokenLimit(Long tokenLimit) { this.tokenLimit = tokenLimit; }
    public Long getTokenUsed() { return tokenUsed; }
    public void setTokenUsed(Long tokenUsed) { this.tokenUsed = tokenUsed; }
    public Integer getTimeWindowSeconds() { return timeWindowSeconds; }
    public void setTimeWindowSeconds(Integer timeWindowSeconds) { this.timeWindowSeconds = timeWindowSeconds; }
    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }
    public Boolean getStatus() { return status; }
    public void setStatus(Boolean status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
