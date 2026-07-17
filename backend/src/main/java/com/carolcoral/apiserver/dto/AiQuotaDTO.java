/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * AI 额度 DTO
 *
 * @author carolcoral
 */
@Schema(description = "AI 额度 DTO")
public class AiQuotaDTO {

    private Long id;

    @NotNull
    private Long userId;

    private Long subscriptionId;

    @NotNull
    @Min(1)
    private Long tokenLimit;

    private Long tokenUsed = 0L;

    @NotNull
    @Min(1)
    private Integer timeWindowSeconds = 18000;

    private Boolean status = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }
    public Long getTokenLimit() { return tokenLimit; }
    public void setTokenLimit(Long tokenLimit) { this.tokenLimit = tokenLimit; }
    public Long getTokenUsed() { return tokenUsed; }
    public void setTokenUsed(Long tokenUsed) { this.tokenUsed = tokenUsed; }
    public Integer getTimeWindowSeconds() { return timeWindowSeconds; }
    public void setTimeWindowSeconds(Integer timeWindowSeconds) { this.timeWindowSeconds = timeWindowSeconds; }
    public Boolean getStatus() { return status; }
    public void setStatus(Boolean status) { this.status = status; }
}
