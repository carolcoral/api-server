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
    public ApiResponse<String> preview(@RequestBody Map<String, String> body) {
        String template = body.get("template");
        if (template == null || template.isEmpty()) {
            return ApiResponse.error("模板内容不能为空");
        }

        String result;
        if ((template.trim().startsWith("{") && template.trim().endsWith("}")) ||
                (template.trim().startsWith("[") && template.trim().endsWith("]"))) {
            result = mockTemplateEngine.processJson(template);
        } else {
            result = mockTemplateEngine.process(template);
        }

        return ApiResponse.success(result);
    }

    /**
     * 批量预览：生成多条随机数据
     */
    @Operation(summary = "批量预览模板渲染结果")
    @PostMapping("/preview/batch")
    public ApiResponse<java.util.List<String>> previewBatch(@RequestBody Map<String, Object> body) {
        String template = (String) body.get("template");
        int count = body.containsKey("count") ? ((Number) body.get("count")).intValue() : 5;

        if (template == null || template.isEmpty()) {
            return ApiResponse.error("模板内容不能为空");
        }
        if (count < 1) count = 1;
        if (count > 100) count = 100;

        java.util.List<String> results = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            String result;
            if ((template.trim().startsWith("{") && template.trim().endsWith("}")) ||
                    (template.trim().startsWith("[") && template.trim().endsWith("]"))) {
                result = mockTemplateEngine.processJson(template);
            } else {
                result = mockTemplateEngine.process(template);
            }
            results.add(result);
        }

        return ApiResponse.success(results);
    }
}
