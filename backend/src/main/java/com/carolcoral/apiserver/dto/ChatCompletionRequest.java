/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completion 请求 DTO
 *
 * @author carolcoral
 */
@Schema(description = "Chat Completion 请求")
public class ChatCompletionRequest {

    @Schema(description = "模型名称，'auto' 表示自动选择", example = "gpt-4o")
    private String model;

    @Schema(description = "消息列表")
    private List<Message> messages;

    @Schema(description = "温度", example = "0.7")
    private Double temperature;

    @JsonProperty("max_tokens")
    @Schema(description = "最大 token 数")
    private Integer maxTokens;

    @Schema(description = "是否流式返回", example = "false")
    private Boolean stream;

    @JsonProperty("top_p")
    @Schema(description = "核采样参数")
    private Double topP;

    @Schema(description = "停止词")
    private List<String> stop;

    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;

    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    @JsonProperty("fallback_strategy")
    @Schema(description = "Fallback 策略: priority/random/cost_first/performance_first")
    private String fallbackStrategy;

    @Schema(description = "额外参数")
    private Map<String, Object> extra;

    public static class Message {
        @Schema(description = "角色: system/user/assistant")
        private String role;

        @Schema(description = "消息内容")
        private String content;

        @Schema(description = "工具调用列表（可选）")
        @JsonProperty("tool_calls")
        private List<Object> toolCalls;

        public Message() {}

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<Object> getToolCalls() { return toolCalls; }
        public void setToolCalls(List<Object> toolCalls) { this.toolCalls = toolCalls; }
    }

    // Getters and Setters
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }
    public Double getTopP() { return topP; }
    public void setTopP(Double topP) { this.topP = topP; }
    public List<String> getStop() { return stop; }
    public void setStop(List<String> stop) { this.stop = stop; }
    public Double getFrequencyPenalty() { return frequencyPenalty; }
    public void setFrequencyPenalty(Double frequencyPenalty) { this.frequencyPenalty = frequencyPenalty; }
    public Double getPresencePenalty() { return presencePenalty; }
    public void setPresencePenalty(Double presencePenalty) { this.presencePenalty = presencePenalty; }
    public String getFallbackStrategy() { return fallbackStrategy; }
    public void setFallbackStrategy(String fallbackStrategy) { this.fallbackStrategy = fallbackStrategy; }
    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }
}
