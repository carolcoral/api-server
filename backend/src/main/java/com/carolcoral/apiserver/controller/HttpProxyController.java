/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.entity.ProxyRecord;
import com.carolcoral.apiserver.repository.ProxyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * HTTP 代理控制器
 * <p>
 * 提供在页面上发起真实 HTTP 请求的功能，自动记录请求和响应数据到 t_proxy_record 表，
 * 用于录制与回放工作流。
 * </p>
 *
 * @author carolcoral
 */
@Tag(name = "HTTP代理", description = "代理发起HTTP请求并自动录制")
@RestController
@RequestMapping("/api/http-proxy")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('record-replay:view')")
public class HttpProxyController {

    private static final Logger log = LoggerFactory.getLogger(HttpProxyController.class);

    private final ObjectMapper objectMapper;
    private final ProxyRecordRepository proxyRecordRepository;

    public HttpProxyController(ObjectMapper objectMapper, ProxyRecordRepository proxyRecordRepository) {
        this.objectMapper = objectMapper;
        this.proxyRecordRepository = proxyRecordRepository;
    }

    /**
     * 代理发起 HTTP 请求并录制
     */
    @Operation(summary = "代理发起HTTP请求", description = "根据用户输入的请求信息代理发送HTTP请求，返回响应并自动录制到t_proxy_record表")
    @PostMapping("/send")
    public ApiResponse<Map<String, Object>> sendRequest(
            @Parameter(description = "请求信息") @RequestBody Map<String, Object> body,
            HttpServletRequest servletRequest) {

        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // 解析请求参数
            String url = (String) body.get("url");
            String method = ((String) body.get("method")).toUpperCase();
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) body.getOrDefault("headers", new LinkedHashMap<>());
            @SuppressWarnings("unchecked")
            Map<String, String> queryParams = (Map<String, String>) body.getOrDefault("queryParams", new LinkedHashMap<>());
            String requestBody = (String) body.get("body");
            String contentType = (String) body.getOrDefault("contentType", "application/json");

            if (url == null || url.trim().isEmpty()) {
                return ApiResponse.error("请求URL不能为空");
            }
            if (method == null || method.trim().isEmpty()) {
                return ApiResponse.error("请求方法不能为空");
            }

            // 拼接查询参数到 URL
            if (!queryParams.isEmpty()) {
                StringBuilder urlWithParams = new StringBuilder(url);
                boolean hasQuery = url.contains("?");
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    urlWithParams.append(hasQuery ? "&" : "?");
                    urlWithParams.append(entry.getKey()).append("=").append(entry.getValue());
                    hasQuery = true;
                }
                url = urlWithParams.toString();
            }

            log.info("代理请求: {} {}", method, url);

            // 发起真实 HTTP 请求
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(true);

            // 设置请求头
            if (contentType != null && !contentType.isEmpty()) {
                connection.setRequestProperty("Content-Type", contentType);
            }
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            // 发送请求体（POST/PUT/PATCH）
            if (requestBody != null && !requestBody.isEmpty()
                    && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }

            // 获取响应
            int statusCode = connection.getResponseCode();
            Map<String, List<String>> responseHeaders = connection.getHeaderFields();
            String responseContentType = connection.getContentType();

            // 读取响应体
            String responseBody;
            try {
                java.io.InputStream is = (statusCode >= 200 && statusCode < 400)
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                if (is == null) {
                    responseBody = "";
                } else {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                    }
                    responseBody = sb.toString().trim();
                }
            } catch (Exception e) {
                responseBody = "";
            }

            connection.disconnect();

            long responseTime = System.currentTimeMillis() - startTime;

            // 提取请求路径（去掉协议和域名）
            String requestPath;
            try {
                URI uri = new URI(url);
                requestPath = uri.getPath();
                if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
                    requestPath += "?" + uri.getQuery();
                }
            } catch (Exception e) {
                requestPath = url;
            }

            // 保存到 t_proxy_record 表
            saveProxyRecord(method, requestPath, statusCode, responseTime,
                    servletRequest, headers, queryParams, requestBody, responseBody, responseContentType);

            // 构建响应结果
            result.put("statusCode", statusCode);
            result.put("responseTime", responseTime);
            result.put("responseHeaders", responseHeaders != null ? filterResponseHeaders(responseHeaders) : new LinkedHashMap<>());
            result.put("responseBody", tryParseJson(responseBody));
            result.put("responseContentType", responseContentType);
            result.put("requestPath", requestPath);

            return ApiResponse.success(result);
        } catch (java.net.UnknownHostException e) {
            log.error("代理请求失败 - 未知主机: {}", e.getMessage());
            return ApiResponse.error("无法解析主机地址，请检查URL是否正确");
        } catch (java.net.ConnectException e) {
            log.error("代理请求失败 - 连接被拒绝: {}", e.getMessage());
            return ApiResponse.error("连接被拒绝，请检查目标服务是否启动");
        } catch (java.net.SocketTimeoutException e) {
            log.error("代理请求失败 - 超时: {}", e.getMessage());
            return ApiResponse.error("请求超时，请检查目标服务是否可达");
        } catch (Exception e) {
            log.error("代理请求失败: {}", e.getMessage(), e);
            return ApiResponse.error("请求失败: " + e.getMessage());
        }
    }

    /**
     * 保存代理请求记录到 t_proxy_record 表
     */
    private void saveProxyRecord(String method, String path, int statusCode, long responseTime,
                                  HttpServletRequest servletRequest, Map<String, String> headers,
                                  Map<String, String> queryParams, String requestBody,
                                  String responseBody, String responseContentType) {
        try {
            ProxyRecord record = new ProxyRecord();
            record.setMethod(method);
            record.setPath(path);
            record.setStatusCode(statusCode);
            record.setResponseTime(responseTime);
            record.setRequestTime(LocalDateTime.now());
            record.setRequestIp(getClientIp(servletRequest));

            // 截断过大的数据
            record.setRequestHeaders(objectMapper.writeValueAsString(headers));
            record.setQueryParams(objectMapper.writeValueAsString(queryParams));
            record.setRequestBody(truncate(requestBody, 10000));
            record.setResponseBody(truncate(responseBody, 50000));
            record.setResponseContentType(responseContentType);

            proxyRecordRepository.save(record);
        } catch (Exception e) {
            log.warn("保存代理录制记录失败: {}", e.getMessage());
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...[truncated]";
    }

    private Object tryParseJson(String text) {
        if (text == null || text.isEmpty()) return "";
        try {
            return objectMapper.readValue(text, Object.class);
        } catch (Exception e) {
            return text;
        }
    }

    private Map<String, String> filterResponseHeaders(Map<String, List<String>> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        if (headers == null) return result;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                result.put(entry.getKey(), String.join(", ", entry.getValue()));
            }
        }
        return result;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
