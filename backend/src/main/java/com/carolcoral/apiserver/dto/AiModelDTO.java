/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * AI 模型 DTO
 *
 * @author carolcoral
 */
@Schema(description = "AI 模型 DTO")
public class AiModelDTO {

    private Long id;

    @NotNull
    private Long providerId;

    @NotBlank
    private String modelName;

    private String displayName;
    private Double inputPrice;
    private Double outputPrice;
    private Integer maxTokens;
    private Boolean supportsStream = true;
    private Boolean autoMode = false;
    private Boolean status = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
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
    public Boolean getAutoMode() { return autoMode; }
    public void setAutoMode(Boolean autoMode) { this.autoMode = autoMode; }
    public Boolean getStatus() { return status; }
    public void setStatus(Boolean status) { this.status = status; }
}
