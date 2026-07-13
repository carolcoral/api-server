/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.controller;

import com.carolcoral.mockserver.dto.ApiResponse;
import com.carolcoral.mockserver.service.MockTemplateEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Mock 模板引擎控制器
 * 提供模板函数列表查询和模板预览功能
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-07-01
 */
@Tag(name = "Mock 模板引擎", description = "Mock 数据模板引擎接口")
@RestController
@RequestMapping("/api/mock-template")
public class MockTemplateController {

    private final MockTemplateEngine mockTemplateEngine;

    public MockTemplateController(MockTemplateEngine mockTemplateEngine) {
        this.mockTemplateEngine = mockTemplateEngine;
    }

    /**
     * 获取所有支持的模板函数列表
     */
    @Operation(summary = "获取支持的模板函数列表")
    @GetMapping("/functions")
    public ApiResponse<Map<String, String>> getFunctions() {
        return ApiResponse.success(mockTemplateEngine.getSupportedFunctions());
    }

    /**
     * 预览模板渲染结果
     */
    @Operation(summary = "预览模板渲染结果")
    @PostMapping("/preview")
    public ApiResponse<String> preview(@RequestBody String body) {
        // 使用 String 接收原始请求体，避免 Fastjson2 解析时触发嵌套占位符检查
        String template = extractTemplate(body);
        if (template == null || template.isEmpty()) {
            return ApiResponse.error("模板内容不能为空");
        }

        String result;
        try {
            if ((template.trim().startsWith("{") && template.trim().endsWith("}")) ||
                    (template.trim().startsWith("[") && template.trim().endsWith("]"))) {
                result = mockTemplateEngine.processJson(template);
            } else {
                result = mockTemplateEngine.process(template);
            }
        } catch (Exception e) {
            // 如果 JSON 解析失败（如包含嵌套占位符等），回退到纯文本处理
            result = mockTemplateEngine.process(template);
        }

        return ApiResponse.success(result);
    }

    /**
     * 批量预览：生成多条随机数据
     */
    @Operation(summary = "批量预览模板渲染结果")
    @PostMapping("/preview/batch")
    public ApiResponse<java.util.List<String>> previewBatch(@RequestBody String body) {
        // 使用 String 接收原始请求体，避免 Fastjson2 解析时触发嵌套占位符检查
        String template = extractTemplate(body);
        int count = extractCount(body);

        if (template == null || template.isEmpty()) {
            return ApiResponse.error("模板内容不能为空");
        }
        if (count < 1) count = 1;
        if (count > 100) count = 100;

        java.util.List<String> results = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            String result;
            try {
                if ((template.trim().startsWith("{") && template.trim().endsWith("}")) ||
                        (template.trim().startsWith("[") && template.trim().endsWith("]"))) {
                    result = mockTemplateEngine.processJson(template);
                } else {
                    result = mockTemplateEngine.process(template);
                }
            } catch (Exception e) {
                // 如果 JSON 解析失败，回退到纯文本处理
                result = mockTemplateEngine.process(template);
            }
            results.add(result);
        }

        return ApiResponse.success(results);
    }

    /**
     * 从 JSON 请求体中提取 template 字段值
     * <p>
     * 使用手动字符串解析避免 Fastjson2 的嵌套占位符检查问题。
     * 当 template 内容本身包含 JSON 对象（含 {} 字符串）时，
     * Fastjson2 可能误判为嵌套占位符并抛出 SyntaxError。
     * </p>
     */
    private String extractTemplate(String jsonBody) {
        if (jsonBody == null || jsonBody.trim().isEmpty()) {
            return null;
        }
        String trimmed = jsonBody.trim();
        // 请求体格式: {"template": "..."} 或 {"template":"..."}
        // 手动解析，完全避开 Fastjson2
        final String key = "\"template\"";
        int keyIdx = trimmed.indexOf(key);
        if (keyIdx < 0) {
            return null;
        }
        // 跳过 key + 冒号
        int colonIdx = trimmed.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) {
            return null;
        }
        // 跳过冒号和空白
        int valueStart = colonIdx + 1;
        while (valueStart < trimmed.length() && (trimmed.charAt(valueStart) == ' ' || trimmed.charAt(valueStart) == '\t')) {
            valueStart++;
        }
        if (valueStart >= trimmed.length() || trimmed.charAt(valueStart) != '"') {
            return null;
        }
        // 找到对应的结束引号（处理转义）
        StringBuilder value = new StringBuilder();
        int i = valueStart + 1;
        while (i < trimmed.length()) {
            char c = trimmed.charAt(i);
            if (c == '\\') {
                if (i + 1 < trimmed.length()) {
                    char next = trimmed.charAt(i + 1);
                    if (next == '"') {
                        value.append('"');
                    } else if (next == '\\') {
                        value.append('\\');
                    } else if (next == 'n') {
                        value.append('\n');
                    } else if (next == 'r') {
                        value.append('\r');
                    } else if (next == 't') {
                        value.append('\t');
                    } else {
                        value.append('\\').append(next);
                    }
                    i += 2;
                    continue;
                }
            } else if (c == '"') {
                break;
            }
            value.append(c);
            i++;
        }
        return value.toString();
    }

    /**
     * 从原始 JSON 请求体中手动提取 count 字段值
     */
    private int extractCount(String jsonBody) {
        if (jsonBody == null) return 5;
        final String key = "\"count\"";
        int keyIdx = jsonBody.indexOf(key);
        if (keyIdx < 0) return 5;
        int colonIdx = jsonBody.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) return 5;
        int valueStart = colonIdx + 1;
        while (valueStart < jsonBody.length() && (jsonBody.charAt(valueStart) == ' ' || jsonBody.charAt(valueStart) == '\t')) {
            valueStart++;
        }
        int valueEnd = valueStart;
        while (valueEnd < jsonBody.length() && Character.isDigit(jsonBody.charAt(valueEnd))) {
            valueEnd++;
        }
        try {
            return Integer.parseInt(jsonBody.substring(valueStart, valueEnd));
        } catch (NumberFormatException e) {
            return 5;
        }
    }
}
