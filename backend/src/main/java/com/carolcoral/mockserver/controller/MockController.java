/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.controller;

import com.carolcoral.mockserver.dto.MockRequest;
import com.carolcoral.mockserver.dto.MockResponseDTO;
import com.carolcoral.mockserver.service.MockService;
import com.carolcoral.mockserver.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Mock请求控制器 - 核心控制器
 * 处理所有Mock请求，不需要认证
 *
 * @author carolcoral
 */
@Tag(name = "Mock请求", description = "Mock请求处理接口（无需认证）")
@RestController
@RequestMapping("/api/mock-server")
@Validated
public class MockController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MockController.class);

    /**
     * 构造器
     */
    public MockController(MockService mockService, SystemConfigService systemConfigService) {
        this.mockService = mockService;
        this.systemConfigService = systemConfigService;
    }

    private final MockService mockService;
    private final SystemConfigService systemConfigService;

    // 默认最大响应延迟时间（毫秒），可从系统配置中动态读取
    private static final int DEFAULT_MAX_RESPONSE_DELAY = 5000;

    /**
     * 处理所有Mock请求
     *
     * @param projectCode 项目编码
     * @param request     HTTP请求
     * @return Mock响应
     */
    @Operation(summary = "处理Mock请求", description = "处理所有Mock请求，支持所有HTTP方法和路径（无需认证）")
    @RequestMapping(value = "/{projectCode}/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.OPTIONS})
    public ResponseEntity<Object> handleMockRequest(
            @Parameter(description = "项目编码", example = "ecmall")
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "项目编码只能包含字母、数字、下划线和连字符") String projectCode,
            HttpServletRequest request) {
        
        try {
            // 获取请求路径（去除项目编码前缀）
            String fullPath = request.getRequestURI();
            String servletPath = request.getServletPath();

            log.info("Mock请求详情 - fullPath: {}, servletPath: {}, projectCode: {}", fullPath, servletPath, projectCode);

            // fullPath 为 /api/mock-server/test/login
            // 构建路径前缀 /api/mock-server/{projectCode}/
            String pathPrefix = "/api/mock-server/" + projectCode + "/";
            String path;

            if (fullPath.startsWith(pathPrefix)) {
                path = fullPath.substring(pathPrefix.length() - 1); // 保留开头的 /
            } else if (fullPath.startsWith("/api/mock-server/")) {
                String mockPathPrefix = "/api/mock-server/" + projectCode;
                if (fullPath.startsWith(mockPathPrefix + "/")) {
                    path = fullPath.substring(mockPathPrefix.length());
                } else if (fullPath.equals(mockPathPrefix)) {
                    path = "/";
                } else {
                    path = "/";
                }
            } else if (servletPath.startsWith("/mock-server/")) {
                if (servletPath.startsWith(pathPrefix)) {
                    path = servletPath.substring(pathPrefix.length() - 1);
                } else {
                    path = servletPath;
                }
            } else {
                path = fullPath.startsWith("/api/mock-server/")
                    ? fullPath.substring("/api/mock-server/".length())
                    : fullPath;
            }

            if (path == null || path.isEmpty()) {
                path = "/";
            }

            String method = request.getMethod();

            log.info("Mock请求: {} {} 项目: {} (解析后的路径)", method, path, projectCode);

            // 构建Mock请求
            MockRequest mockRequest = buildMockRequest(projectCode, path, method, request);

            // 处理请求
            MockResponseDTO mockResponse = mockService.handleMockRequest(mockRequest, request);

            // 构建HTTP响应
            return buildHttpResponse(mockResponse);

        } catch (Exception e) {
            log.error("处理Mock请求失败");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorBody(500, "服务器内部错误"));
        }
    }

    /**
     * 构建Mock请求
     *
     * @param projectCode 项目编码
     * @param path        请求路径
     * @param method      请求方法
     * @param request     HTTP请求
     * @return Mock请求
     */
    private MockRequest buildMockRequest(String projectCode, String path, String method, HttpServletRequest request) {
        MockRequest mockRequest = new MockRequest();
        mockRequest.setProjectCode(projectCode);
        mockRequest.setPath(path);
        mockRequest.setMethod(method);

        // 获取请求头
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        mockRequest.setHeaders(headers);

        // 获取请求参数
        Map<String, String[]> parameterMap = request.getParameterMap();
        Map<String, Object> params = new HashMap<>();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String[] values = entry.getValue();
            if (values.length == 1) {
                params.put(entry.getKey(), values[0]);
            } else {
                params.put(entry.getKey(), values);
            }
        }
        mockRequest.setParams(params);

        // 获取路径参数（RESTful风格）
        Map<String, String> pathParams = new HashMap<>();
        String requestURI = request.getRequestURI();
        String contextPath = request.getContextPath();
        String pathWithoutContext = requestURI.startsWith(contextPath) ? requestURI.substring(contextPath.length()) : requestURI;

        // 从请求URI中提取mock-server后的部分
        String afterMockServer = "";
        if (pathWithoutContext.startsWith("/mock-server/")) {
            int idx = pathWithoutContext.indexOf("/", "/mock-server/".length());
            if (idx != -1) {
                afterMockServer = pathWithoutContext.substring(idx + 1);
            }
        }

        // 解析路径参数 - 从实际请求路径中提取值
        if (!afterMockServer.isEmpty()) {
            String[] requestParts = afterMockServer.split("/");
            // 需要找到对应的接口模板路径来知道哪些段是占位符
            // 这里先简单处理：将所有段值都记录下来，后续匹配时会用到
            for (int i = 0; i < requestParts.length; i++) {
                log.debug("路径段 {}: {}", i, requestParts[i]);
            }
        }
        mockRequest.setPathParams(pathParams);

        // 获取请求体
        try {
            java.io.BufferedReader reader = request.getReader();
            StringBuilder bodyBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                bodyBuilder.append(line);
            }
            String bodyStr = bodyBuilder.toString();
            if (!bodyStr.isEmpty()) {
                // 尝试解析为JSON对象
                try {
                    mockRequest.setBody(new com.fasterxml.jackson.databind.ObjectMapper().readValue(bodyStr, Object.class));
                } catch (Exception e) {
                    mockRequest.setBody(bodyStr);
                }
            }
        } catch (Exception e) {
            log.debug("无法读取请求体: {}", e.getMessage());
        }

        return mockRequest;
    }

    /**
     * 构建HTTP响应
     *
     * @param mockResponse Mock响应
     * @return HTTP响应
     */
    private ResponseEntity<Object> buildHttpResponse(MockResponseDTO mockResponse) {
        // 设置延迟（如果配置了）
        if (mockResponse.getDelay() != null && mockResponse.getDelay() > 0) {
            try {
                // 限制最大延迟时间，防止DoS攻击（从系统配置读取，默认5000ms）
                int maxDelay = getMaxResponseDelay();
                long delay = Math.min(mockResponse.getDelay(), maxDelay);
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("响应延迟被中断");
            }
        }

        // 构建响应头
        HttpHeaders headers = new HttpHeaders();
        if (mockResponse.getHeaders() != null) {
            mockResponse.getHeaders().forEach(headers::add);
        }

        // 默认Content-Type
        if (!headers.containsKey("Content-Type")) {
            headers.add("Content-Type", "application/json;charset=UTF-8");
        }

        // 构建响应
        return ResponseEntity.status(mockResponse.getStatusCode())
                .headers(headers)
                .body(mockResponse.getBody());
    }

    /**
     * 创建错误响应体
     *
     * @param statusCode 状态码
     * @param message    错误消息
     * @return 错误响应体
     */
    private Map<String, Object> createErrorBody(int statusCode, String message) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("code", statusCode);
        errorBody.put("message", message);
        errorBody.put("timestamp", System.currentTimeMillis());
        return errorBody;
    }

    /**
     * 从系统配置中获取最大响应延迟时间（毫秒）
     * <p>如果配置不存在或无法解析，返回默认值 5000ms。</p>
     *
     * @return 最大响应延迟时间
     */
    private int getMaxResponseDelay() {
        String configValue = systemConfigService.getConfig("maxResponseDelay");
        if (configValue != null && !configValue.isEmpty()) {
            try {
                return Integer.parseInt(configValue);
            } catch (NumberFormatException e) {
                log.warn("maxResponseDelay 配置值 '{}' 无法解析，使用默认值 {}", configValue, DEFAULT_MAX_RESPONSE_DELAY);
            }
        }
        return DEFAULT_MAX_RESPONSE_DELAY;
    }
}
