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
 * AI 调用日志实体
 *
 * @author carolcoral
 * @since 2026-06-25
 */
@Entity
@Table(name = "t_ai_call_log")
@Schema(description = "AI 调用日志")
public class AiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "主键ID")
    private Long id;

    @Column(name = "user_id", nullable = false)
    @Schema(description = "调用用户ID")
    private Long userId;

    @Column(name = "username", length = 100)
    @Schema(description = "调用用户名")
    private String username;

    @Column(name = "api_type", length = 50, nullable = false)
    @Schema(description = "API 类型：chat / generate-response / generate-description / generate-email-template / generate-code-template")
    private String apiType;

    @Column(name = "call_time", nullable = false)
    @Schema(description = "调用时间")
    private LocalDateTime callTime;

    @Column(name = "success")
    @Schema(description = "是否调用成功")
    private Boolean success;

    @Column(name = "error_message", length = 500)
    @Schema(description = "错误信息")
    private String errorMessage;

    public AiCallLog() {
    }

    public AiCallLog(Long userId, String username, String apiType, LocalDateTime callTime, Boolean success, String errorMessage) {
        this.userId = userId;
        this.username = username;
        this.apiType = apiType;
        this.callTime = callTime;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getApiType() { return apiType; }
    public void setApiType(String apiType) { this.apiType = apiType; }

    public LocalDateTime getCallTime() { return callTime; }
    public void setCallTime(LocalDateTime callTime) { this.callTime = callTime; }

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
