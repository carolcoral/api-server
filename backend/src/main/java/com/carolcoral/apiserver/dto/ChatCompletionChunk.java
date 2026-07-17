/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * OpenAI Chat Completion 流式 chunk DTO
 *
 * @author carolcoral
 */
@Schema(description = "Chat Completion 流式 chunk")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionChunk {

    private String id;
    private String object = "chat.completion.chunk";
    private Long created;
    private String model;

    @JsonProperty("system_fingerprint")
    private String systemFingerprint;

    private List<ChunkChoice> choices;

    public static class ChunkChoice {
        private int index;
        private Delta delta;

        @JsonProperty("finish_reason")
        private String finishReason;

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
        public Delta getDelta() { return delta; }
        public void setDelta(Delta delta) { this.delta = delta; }
        public String getFinishReason() { return finishReason; }
        public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
    }

    public static class Delta {
        private String role;
        private String content;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }
    public Long getCreated() { return created; }
    public void setCreated(Long created) { this.created = created; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSystemFingerprint() { return systemFingerprint; }
    public void setSystemFingerprint(String systemFingerprint) { this.systemFingerprint = systemFingerprint; }
    public List<ChunkChoice> getChoices() { return choices; }
    public void setChoices(List<ChunkChoice> choices) { this.choices = choices; }
}
