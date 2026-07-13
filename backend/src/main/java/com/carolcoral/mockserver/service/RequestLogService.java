/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.service;

import com.carolcoral.mockserver.dto.MockResponseDTO;
import com.carolcoral.mockserver.entity.MockApi;
import com.carolcoral.mockserver.entity.Project;
import com.carolcoral.mockserver.entity.RequestLog;
import com.carolcoral.mockserver.repository.RequestLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求日志服务类
 * 用于记录和统计接口请求日志
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Service
public class RequestLogService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RequestLogService.class);

    /**
     * 构造器
     */
    public RequestLogService(RequestLogRepository requestLogRepository, ObjectMapper objectMapper) {
        this.requestLogRepository = requestLogRepository;
        this.objectMapper = objectMapper;
    }

    private final RequestLogRepository requestLogRepository;
    private final ObjectMapper objectMapper;

    // DDoS 防护：每个项目每分钟最多录制 100 条请求详情
    private static final int MAX_RECORDS_PER_MINUTE = 100;
    // 请求体最大存储大小 10KB
    private static final int MAX_BODY_SIZE = 10000;
    private final ConcurrentHashMap<String, Long> projectRecordCount = new ConcurrentHashMap<>();
    private volatile long recordWindowStart = System.currentTimeMillis();

    /**
     * 异步记录请求日志（不含请求/响应体）
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logRequestAsync(MockApi mockApi, HttpServletRequest request, int statusCode, long responseTime, Long userId) {
        logRequestWithDetailsAsync(mockApi.getProject(), mockApi, request, null, statusCode, responseTime, userId);
    }

    /**
     * 异步记录请求日志（含完整请求/响应数据，用于录制回放）
     *
     * @param project 项目（可能为null）
     * @param mockApi 接口信息（404时为null）
     * @param request HTTP请求对象
     * @param mockResponse Mock响应对象
     * @param statusCode 响应状态码
     * @param responseTime 响应时间（毫秒）
     * @param userId 用户ID（可选）
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logRequestWithDetailsAsync(Project project, MockApi mockApi, HttpServletRequest request,
                                           MockResponseDTO mockResponse, int statusCode, long responseTime, Long userId) {
        try {
            // DDoS 防护：每分钟窗口重置
            long now = System.currentTimeMillis();
            if (now - recordWindowStart > 60000) {
                projectRecordCount.clear();
                recordWindowStart = now;
            }

            // 按项目限流
            String projectKey = project != null ? "p:" + project.getCode() : "unknown";
            Long count = projectRecordCount.compute(projectKey, (k, v) -> v == null ? 1 : v + 1);
            boolean shouldRecordDetails = count <= MAX_RECORDS_PER_MINUTE;

            String requestHeaders = null;
            String queryParams = null;
            String requestBody = null;
            String responseBody = null;
            String responseContentType = null;

            if (shouldRecordDetails) {
                try {
                    // 采集请求头
                    Map<String, String> headersMap = new HashMap<>();
                    Enumeration<String> headerNames = request.getHeaderNames();
                    while (headerNames.hasMoreElements()) {
                        String name = headerNames.nextElement();
                        headersMap.put(name, request.getHeader(name));
                    }
                    requestHeaders = objectMapper.writeValueAsString(headersMap);

                    // 采集查询参数
                    Map<String, String[]> paramMap = request.getParameterMap();
                    Map<String, Object> queryMap = new HashMap<>();
                    for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
                        String[] values = entry.getValue();
                        queryMap.put(entry.getKey(), values.length == 1 ? values[0] : values);
                    }
                    queryParams = objectMapper.writeValueAsString(queryMap);

                    // 采集请求体（限制大小）
                    StringBuilder bodyBuilder = new StringBuilder();
                    try (BufferedReader reader = request.getReader()) {
                        String line;
                        int totalRead = 0;
                        while ((line = reader.readLine()) != null) {
                            bodyBuilder.append(line);
                            totalRead += line.length();
                            if (totalRead > MAX_BODY_SIZE) break;
                        }
                    } catch (Exception ignored) {
                        // 请求体可能已被消费，忽略
                    }
                    String bodyStr = bodyBuilder.toString();
                    if (!bodyStr.isEmpty()) {
                        requestBody = bodyStr.length() > MAX_BODY_SIZE ? bodyStr.substring(0, MAX_BODY_SIZE) : bodyStr;
                    }

                    // 采集响应体（限制大小）
                    if (mockResponse != null && mockResponse.getBody() != null) {
                        responseBody = objectMapper.writeValueAsString(mockResponse.getBody());
                        if (responseBody.length() > MAX_BODY_SIZE) {
                            responseBody = responseBody.substring(0, MAX_BODY_SIZE);
                        }
                        responseContentType = mockResponse.getHeaders() != null
                                ? mockResponse.getHeaders().get("Content-Type") : null;
                    }
                } catch (Exception e) {
                    log.debug("采集请求详情失败（不影响日志记录）: {}", e.getMessage());
                }
            }

            String method = mockApi != null ? mockApi.getMethod().name() : request.getMethod();
            String path = mockApi != null ? mockApi.getPath() : request.getRequestURI();

            RequestLog logEntry = RequestLog.builder()
                    .mockApiId(mockApi != null ? mockApi.getId() : null)
                    .projectId(project != null ? project.getId() : null)
                    .method(method)
                    .path(path)
                    .requestTime(LocalDateTime.now())
                    .statusCode(statusCode)
                    .responseTime(responseTime)
                    .requestIp(getClientIp(request))
                    .userId(userId)
                    .requestHeaders(requestHeaders)
                    .queryParams(queryParams)
                    .requestBody(requestBody)
                    .responseBody(responseBody)
                    .responseContentType(responseContentType)
                    .build();

            requestLogRepository.save(logEntry);
            log.debug("请求日志已记录: 项目={}, 方法={}, 路径={}, 状态码={}, 响应时间={}ms, 详情={}",
                    project != null ? project.getCode() : "unknown", method, path, statusCode, responseTime,
                    shouldRecordDetails ? "完整" : "摘要");
        } catch (Exception e) {
            // 记录日志失败不应该影响主流程
            log.error("记录请求日志失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取今天的请求数量
     *
     * @return 今天的请求数量
     */
    public long getTodayRequestCount() {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime todayEnd = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        return requestLogRepository.countTodayRequests(todayStart, todayEnd);
    }

    /**
     * 获取指定项目和今天的请求数量
     *
     * @param projectId 项目ID
     * @return 指定项目今天的请求数量
     */
    public long getTodayRequestCountByProject(Long projectId) {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime todayEnd = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        return requestLogRepository.countByProjectIdAndRequestTimeBetween(projectId, todayStart, todayEnd);
    }

    /**
     * 获取客户端IP地址
     *
     * @param request HTTP请求对象
     * @return 客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多个IP的情况（X-Forwarded-For可能包含多个IP）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
