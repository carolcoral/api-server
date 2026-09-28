/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MockTemplateEngine 单元测试：占位符替换、缓存一致性、JSON 递归处理与异常兜底
 *
 * @author carolcoral
 */
class MockTemplateEngineTest {

    private final MockTemplateEngine engine = new MockTemplateEngine();

    @Test
    @DisplayName("空模板原样返回")
    void processEmptyTemplate() {
        assertNull(engine.process(null));
        assertEquals("", engine.process(""));
    }

    @Test
    @DisplayName("无占位符的文本原样返回")
    void processPlainText() {
        assertEquals("hello world", engine.process("hello world"));
    }

    @Test
    @DisplayName("未知占位符被剥离花括号，不破坏模板其余内容")
    void processUnknownPlaceholder() {
        assertEquals("not_exists", engine.process("{{not_exists}}"));
        assertEquals("前缀 not_exists 后缀", engine.process("前缀 {{not_exists}} 后缀"));
    }

    @Test
    @DisplayName("同一占位符在同一模板中取值一致")
    void samePlaceholderReusesCachedValue() {
        String result = engine.process("{\"a\":\"{{name}}\",\"b\":\"{{name}}\"}");
        JSONObject obj = JSON.parseObject(result);
        assertEquals(obj.getString("a"), obj.getString("b"));
    }

    @Test
    @DisplayName("整数占位符遵循指定范围")
    void integerRespectsRange() {
        for (int i = 0; i < 50; i++) {
            String result = engine.process("{{integer:10-20}}");
            int value = Integer.parseInt(result);
            assertTrue(value >= 10 && value <= 20, "越界取值: " + value);
        }
    }

    @Test
    @DisplayName("日期与时间占位符输出固定格式")
    void dateAndTimeOutputShape() {
        assertTrue(engine.process("{{date}}").matches("\\d{4}-\\d{2}-\\d{2}"));
        assertTrue(engine.process("{{datetime}}").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertTrue(engine.process("{{time}}").matches("\\d{2}:\\d{2}:\\d{2}"));
    }

    @ParameterizedTest
    @DisplayName("常用占位符均能生成非空结果")
    @ValueSource(strings = {
            "{{name}}", "{{firstName}}", "{{lastName}}", "{{phone}}", "{{email}}",
            "{{idCard}}", "{{province}}", "{{city}}", "{{address}}", "{{zipCode}}",
            "{{company}}", "{{job}}", "{{age}}", "{{uuid}}", "{{boolean}}",
            "{{decimal:0-100}}", "{{timestamp}}"
    })
    void commonPlaceholdersProduceOutput(String placeholder) {
        String result = engine.process(placeholder);
        assertNotNull(result);
        assertFalse(result.isBlank());
        assertNotEquals(placeholder, result, "占位符未被替换: " + placeholder);
    }

    @Test
    @DisplayName("纯文本 JSON 模板中无占位符时保持结构")
    void processJsonWithoutPlaceholder() {
        String result = engine.processJson("{\"name\":\"fixed\"}");
        assertEquals("fixed", JSON.parseObject(result).getString("name"));
    }

    @Test
    @DisplayName("JSON 模板递归替换字符串、数组与嵌套对象")
    void processJsonRecursively() {
        String template = "{\"user\":{\"name\":\"{{name}}\"},\"list\":[\"{{city}}\",123],\"raw\":true}";
        JSONObject obj = JSON.parseObject(engine.processJson(template));
        assertNotNull(obj.getJSONObject("user").getString("name"));
        JSONArray list = obj.getJSONArray("list");
        assertEquals(2, list.size());
        assertEquals(123, list.getIntValue(1));
        assertTrue(obj.getBooleanValue("raw"));
    }

    @Test
    @DisplayName("JSON 模板解析失败时回退到纯文本处理")
    void processJsonFallsBackToText() {
        String broken = "not-a-json {{city}}";
        String result = engine.processJson(broken);
        assertFalse(result.contains("{{city}}"), "回退处理未替换占位符: " + result);
    }

    @Test
    @DisplayName("processJson 空输入原样返回")
    void processJsonEmpty() {
        assertNull(engine.processJson(null));
        assertEquals("", engine.processJson(""));
    }

    @Test
    @DisplayName("支持的函数清单包含常用占位符")
    void supportedFunctionsAreExposed() {
        Map<String, String> functions = engine.getSupportedFunctions();
        assertFalse(functions.isEmpty());
        assertTrue(functions.containsKey("{{name}}"));
        assertTrue(functions.containsKey("{{phone}}"));
    }

    @Test
    @DisplayName("占位符前后空格被正确识别")
    void placeholderWithSpaces() {
        assertNotEquals("{{ name }}", engine.process("{{ name }}"));
    }
}
