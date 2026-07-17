/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * AI 订阅 DTO
 *
 * @author carolcoral
 */
@Schema(description = "AI 订阅 DTO")
public class AiSubscriptionDTO {

    private Long id;

    @NotNull
    private Long userId;

    @NotNull
    private Long providerId;

    @NotNull
    private Long modelId;

    private Integer priority = 0;
    private Integer weight = 1;
    private String tags;
    private Boolean fallbackEnabled = true;
    private Integer maxTokensPerRequest;
    private Boolean status = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
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
}
