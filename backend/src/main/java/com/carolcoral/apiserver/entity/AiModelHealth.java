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
 * AI 模型健康检查记录实体类
 *
 * @author carolcoral
 */
@Schema(description = "AI 模型健康检查记录实体")
@Entity
@Table(name = "t_ai_model_health")
public class AiModelHealth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", nullable = false)
    private AiModel model;

    @Column(nullable = false)
    private LocalDateTime checkTime;

    @Column(nullable = false, length = 20)
    private String status;

    private Long latencyMs;

    @Column(length = 500)
    private String errorMsg;

    public AiModelHealth() {}

    @PrePersist
    protected void onCreate() {
        if (checkTime == null) {
            checkTime = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AiModel getModel() { return model; }
    public void setModel(AiModel model) { this.model = model; }
    public LocalDateTime getCheckTime() { return checkTime; }
    public void setCheckTime(LocalDateTime checkTime) { this.checkTime = checkTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
}
