/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.repository.UserRepository;
import com.carolcoral.apiserver.service.AiService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI 功能控制器
 *
 * @author carolcoral
 * @since 2026-06-23
 */
@Tag(name = "AI 功能", description = "AI 智能生成等接口")
@RestController
@RequestMapping("/api/ai")
@PreAuthorize("isAuthenticated()")
public class AiMockController {

    private static final Logger log = LoggerFactory.getLogger(AiMockController.class);

    @Autowired
    private AiService aiService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    /**
     * 从 SecurityContext 获取当前登录用户信息
     */
    private String getCurrentUsername() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    private Long getCurrentUserId() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof User) {
                return ((User) principal).getId();
            }
            String username = getCurrentUsername();
            if (!"unknown".equals(username)) {
                Optional<User> userOpt = userRepository.findByUsername(username);
                if (userOpt.isPresent()) {
                    return userOpt.get().getId();
                }
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    /**
     * 记录 AI 调用日志（使用 JdbcTemplate 直接 INSERT，避免 JPA ID 生成策略与数据库的兼容问题）
     */
    private void logAiCall(String apiType, boolean success, String errorMessage) {
        try {
            jdbcTemplate.update(
                "INSERT INTO t_ai_call_log (user_id, username, api_type, call_time, success, error_message) VALUES (?, ?, ?, ?, ?, ?)",
                getCurrentUserId(),
                getCurrentUsername(),
                apiType,
                LocalDateTime.now(),
                success,
                errorMessage != null && errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage
            );
        } catch (Exception e) {
            log.warn("记录 AI 调用日志失败: type={}, error={}", apiType, e.getMessage(), e);
        }
    }

    /**
     * AI 生成 Mock 响应数据
     */
    @Operation(summary = "AI 智能生成 Mock 响应数据")
    @PostMapping("/generate-response")
    public ApiResponse<List<Map<String, Object>>> generateMockResponse(@RequestBody Map<String, Object> params,
                                                                       HttpServletRequest request) {
        String apiMethod = (String) params.get("apiMethod");
        String apiPath = (String) params.get("apiPath");
        String apiName = (String) params.get("apiName");
        String description = (String) params.get("description");
        Integer count = params.get("count") != null ? ((Number) params.get("count")).intValue() : 3;

        if (apiMethod == null || apiMethod.isBlank()) {
            return ApiResponse.error("请提供接口请求方法 (apiMethod)");
        }
        if (apiPath == null || apiPath.isBlank()) {
            return ApiResponse.error("请提供接口路径 (apiPath)");
        }

        try {
            List<Map<String, Object>> responses = aiService.generateMockResponse(
                    apiMethod, apiPath, apiName, description, count);
            logAiCall("generate-response", true, null);
            return ApiResponse.success(responses);
        } catch (RuntimeException e) {
            logAiCall("generate-response", false, e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * AI 生成接口描述（流式 SSE）
     */
    @Hidden
    @Operation(summary = "AI 智能生成接口描述（流式）", description = "通过 SSE 实时推送生成结果，避免长时间等待和超时")
    @PostMapping("/generate-description/stream")
    public void generateDescriptionStream(@RequestBody Map<String, String> params,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        String apiMethod = params.get("apiMethod");
        String apiPath = params.get("apiPath");
        String apiName = params.get("apiName");

        if (apiMethod == null || apiMethod.isBlank()) {
            writeStreamError(response, "请提供接口请求方法 (apiMethod)");
            return;
        }
        if (apiPath == null || apiPath.isBlank()) {
            writeStreamError(response, "请提供接口路径 (apiPath)");
            return;
        }

        executeStreamRequest(response, "generate-description-stream",
                () -> aiService.generateApiDescriptionStream(apiMethod, apiPath, apiName));
    }

    /**
     * AI 生成邮件模板（流式 SSE）
     */
    @Hidden
    @Operation(summary = "AI 智能生成邮件模板（流式）", description = "通过 SSE 实时推送生成结果，避免长时间等待和超时")
    @PostMapping("/generate-email-template/stream")
    public void generateEmailTemplateStream(@RequestBody Map<String, String> params,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        String templateType = params.get("templateType");
        String templateName = params.get("templateName");
        String existingSubject = params.get("existingSubject");
        String existingContent = params.get("existingContent");
        String customPrompt = params.get("customPrompt");

        if (templateType == null || templateType.isBlank()) {
            writeStreamError(response, "请提供模板类型 (templateType)");
            return;
        }

        executeStreamRequest(response, "generate-email-template-stream",
                () -> aiService.generateEmailTemplateStream(templateType, templateName, existingSubject, existingContent, customPrompt));
    }

    /**
     * AI 生成代码模板（流式 SSE）
     */
    @Hidden
    @Operation(summary = "AI 智能生成代码模板（流式）", description = "通过 SSE 实时推送生成结果，避免长时间等待和超时")
    @PostMapping("/generate-code-template/stream")
    public void generateCodeTemplateStream(@RequestBody Map<String, Object> params,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        String apiMethod = (String) params.get("apiMethod");
        String apiPath = (String) params.get("apiPath");
        String apiName = (String) params.get("apiName");
        String description = (String) params.get("description");
        String transformerType = (String) params.get("transformerType");
        String existingSourceCode = (String) params.get("existingSourceCode");

        executeStreamRequest(response, "generate-code-template-stream",
                () -> aiService.generateCodeTemplateStream(apiMethod, apiPath, apiName, description, transformerType, existingSourceCode));
    }

    // ==================== SSE 工具方法 ====================

    @FunctionalInterface
    private interface StreamSupplier {
        BufferedReader get() throws Exception;
    }

    private void executeStreamRequest(HttpServletResponse response, String apiType, StreamSupplier supplier) {
        PrintWriter writer = null;
        BufferedReader reader = null;
        try {
            writer = initSseResponse(response);
            reader = supplier.get();
            streamToClient(writer, reader);
            logAiCall(apiType, true, null);
        } catch (RuntimeException e) {
            log.error("AI 流式生成失败 [{}]: {}", apiType, e.getMessage());
            logAiCall(apiType, false, e.getMessage());
            writeError(writer, e.getMessage());
        } catch (Exception e) {
            log.error("AI 流式生成失败 [{}]: {}", apiType, e.getMessage(), e);
            logAiCall(apiType, false, e.getMessage());
            writeError(writer, e.getMessage());
        } finally {
            closeStreams(reader, writer);
        }
    }

    private PrintWriter initSseResponse(HttpServletResponse response) throws IOException {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        return response.getWriter();
    }

    private void writeStreamError(HttpServletResponse response, String message) {
        try {
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            PrintWriter pw = response.getWriter();
            pw.write("data: [ERROR] " + message + "\n\n");
            pw.flush();
            pw.close();
        } catch (Exception ignored) {}
    }

    private void writeError(PrintWriter writer, String message) {
        if (writer != null) {
            try {
                writer.write("data: [ERROR] " + message + "\n\n");
                writer.flush();
            } catch (Exception ignored) {}
        }
    }

    private void streamToClient(PrintWriter writer, BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            writer.write(line + "\n");
            writer.flush();
            if (line.contains("[DONE]")) {
                break;
            }
        }
    }

    private void closeStreams(BufferedReader reader, PrintWriter writer) {
        if (reader != null) {
            try { reader.close(); } catch (IOException ignored) {}
        }
        if (writer != null) {
            try { writer.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * 获取 AI 对话建议问题（基于 README + CHANGELOG 生成并缓存）
     */
    @Operation(summary = "获取 AI 对话建议问题", description = "基于系统 README 和 CHANGELOG 生成建议问题列表，缓存后版本更新时自动刷新")
    @GetMapping("/chat-suggestions")
    public ApiResponse<List<String>> getChatSuggestions() {
        try {
            List<String> suggestions = aiService.getChatSuggestions();
            return ApiResponse.success(suggestions);
        } catch (Exception e) {
            log.error("获取建议问题失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取建议问题失败");
        }
    }

    /**
     * AI 通用对话接口
     */
    @Operation(summary = "AI 通用对话", description = "通用 AI 对话接口，支持多轮上下文，传入 messages 列表")
    @PostMapping("/chat")
    public ApiResponse<Map<String, String>> chat(@RequestBody Map<String, Object> params,
                                                  HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = (List<Map<String, String>>) params.get("messages");

        if (messages == null || messages.isEmpty()) {
            return ApiResponse.error("请提供对话消息列表 (messages)");
        }

        // 支持指定 AI 配置和模型
        Long configId = null;
        if (params.get("configId") != null) {
            if (params.get("configId") instanceof Number) {
                configId = ((Number) params.get("configId")).longValue();
            }
        }
        String model = (String) params.get("model");

        try {
            String reply = aiService.chat(messages, configId, model);
            Map<String, String> result = new LinkedHashMap<>();
            result.put("reply", reply);
            logAiCall("chat", true, null);
            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            log.error("AI 对话失败: {}", e.getMessage());
            logAiCall("chat", false, e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("AI 对话失败: {}", e.getMessage(), e);
            logAiCall("chat", false, e.getMessage());
            return ApiResponse.error("AI 对话失败: " + e.getMessage());
        }
    }

    /**
     * AI 流式对话接口（SSE）
     * 实时逐 token 推送 AI 回复，避免长时间等待和超时
     * 支持指定 configId 和 model 进行切换
     */
    @Hidden
    @Operation(summary = "AI 流式对话（SSE）", description = "流式 AI 对话，通过 SSE 实时推送每个 token")
    @PostMapping("/chat/stream")
    public void chatStream(@RequestBody Map<String, Object> params,
                            HttpServletRequest request,
                            HttpServletResponse response) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = (List<Map<String, String>>) params.get("messages");

        // 支持指定 AI 配置和模型
        Long configId = null;
        if (params.get("configId") != null) {
            if (params.get("configId") instanceof Number) {
                configId = ((Number) params.get("configId")).longValue();
            }
        }
        String model = (String) params.get("model");

        // 设置 SSE 响应头
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no"); // 禁用 nginx 缓冲

        PrintWriter writer = null;
        BufferedReader reader = null;
        try {
            writer = response.getWriter();
            reader = aiService.chatStream(messages, configId, model);

            String line;
            while ((line = reader.readLine()) != null) {
                // 逐行透传 SSE 数据
                writer.write(line + "\n");
                writer.flush();

                // 检测 [DONE] 标记
                if (line.contains("[DONE]")) {
                    break;
                }
            }
            logAiCall("chat-stream", true, null);
        } catch (RuntimeException e) {
            log.error("AI 流式对话失败: {}", e.getMessage());
            logAiCall("chat-stream", false, e.getMessage());
            try {
                if (writer != null) {
                    writer.write("data: [ERROR] " + e.getMessage() + "\n\n");
                    writer.flush();
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.error("AI 流式对话失败: {}", e.getMessage(), e);
            logAiCall("chat-stream", false, e.getMessage());
            try {
                if (writer != null) {
                    writer.write("data: [ERROR] " + e.getMessage() + "\n\n");
                    writer.flush();
                }
            } catch (Exception ignored) {}
        } finally {
            // 关闭流
            if (reader != null) {
                try { reader.close(); } catch (IOException ignored) {}
            }
            if (writer != null) {
                try { writer.close(); } catch (Exception ignored) {}
            }
        }
    }
}
