package com.carolcoral.apiserver.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * 消息 content 字段反序列化器。
 * 兼容两种格式：
 * 1. 纯文本字符串: "content": "hello"
 * 2. 多模态内容数组: "content": [{"type":"text","text":"hello"}]
 *
 * @author carolcoral
 */
public class ContentDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_STRING) {
            return p.getValueAsString();
        }
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.START_ARRAY) {
            StringBuilder sb = new StringBuilder();
            JsonNode node = p.getCodec().readTree(p);
            for (JsonNode part : node) {
                String type = part.has("type") ? part.get("type").asText() : "text";
                if ("text".equals(type) && part.has("text")) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(part.get("text").asText());
                }
            }
            return sb.toString();
        }
        // 兜底：尝试作为 Object 解析
        JsonNode node = p.getCodec().readTree(p);
        return node.asText();
    }
}
