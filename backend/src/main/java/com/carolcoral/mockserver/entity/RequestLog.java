/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 请求日志实体类
 * 用于记录自定义接口的请求日志
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Schema(description = "请求日志实体")
@Entity
@Table(name = "t_request_log", indexes = {
    @Index(name = "idx_mock_api_id", columnList = "mock_api_id"),
    @Index(name = "idx_request_time", columnList = "request_time")
})
public class RequestLog {

    @Schema(description = "日志ID", example = "1")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "请求的接口ID（404时为空）", example = "1")
    @Column(name = "mock_api_id")
    private Long mockApiId;

    @Schema(description = "所属项目ID", example = "1")
    @Column(name = "project_id")
    private Long projectId;

    @Schema(description = "请求方法", example = "GET")
    @Column(nullable = false, length = 10, name = "method")
    private String method;

    @Schema(description = "请求路径", example = "/api/user/login")
    @Column(nullable = false, length = 200, name = "path")
    private String path;

    @Schema(description = "请求时间")
    @Column(nullable = false, name = "request_time")
    private LocalDateTime requestTime;

    @Schema(description = "响应状态码", example = "200")
    @Column(name = "status_code")
    private Integer statusCode;

    @Schema(description = "响应时间（毫秒）", example = "150")
    @Column(name = "response_time")
    private Long responseTime;

    @Schema(description = "请求IP", example = "192.168.1.1")
    @Column(length = 50, name = "request_ip")
    private String requestIp;

    @Schema(description = "用户ID（如果已登录）", example = "1")
    @Column(name = "user_id")
    private Long userId;

    @Schema(description = "请求头（JSON格式）")
    @Column(name = "request_headers", columnDefinition = "TEXT")
    private String requestHeaders;

    @Schema(description = "请求查询参数（JSON格式）")
    @Column(name = "query_params", columnDefinition = "TEXT")
    private String queryParams;

    @Schema(description = "请求体")
    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Schema(description = "响应体")
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Schema(description = "响应Content-Type")
    @Column(name = "response_content_type", length = 100)
    private String responseContentType;

    @ManyToOne
    @JoinColumn(name = "mock_api_id", insertable = false, updatable = false)
    @Schema(description = "请求的接口")
    private MockApi mockApi;

    /**
     * 持久化前回调方法
     * 设置请求时间
     */
    @PrePersist
    protected void onCreate() {
        if (requestTime == null) {
            requestTime = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMockApiId() {
        return mockApiId;
    }

    public void setMockApiId(Long mockApiId) {
        this.mockApiId = mockApiId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public Long getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(Long responseTime) {
        this.responseTime = responseTime;
    }

    public String getRequestIp() {
        return requestIp;
    }

    public void setRequestIp(String requestIp) {
        this.requestIp = requestIp;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(String requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public String getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(String queryParams) {
        this.queryParams = queryParams;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public String getResponseContentType() {
        return responseContentType;
    }

    public void setResponseContentType(String responseContentType) {
        this.responseContentType = responseContentType;
    }

    public MockApi getMockApi() {
        return mockApi;
    }

    public void setMockApi(MockApi mockApi) {
        this.mockApi = mockApi;
    }

    /**
     * Builder方法
     */
    public static RequestLogBuilder builder() {
        return new RequestLogBuilder();
    }

    public static class RequestLogBuilder {
        private Long mockApiId;
        private Long projectId;
        private String method;
        private String path;
        private LocalDateTime requestTime;
        private Integer statusCode;
        private Long responseTime;
        private String requestIp;
        private Long userId;
        private String requestHeaders;
        private String queryParams;
        private String requestBody;
        private String responseBody;
        private String responseContentType;

        public RequestLogBuilder mockApiId(Long mockApiId) {
            this.mockApiId = mockApiId;
            return this;
        }

        public RequestLogBuilder projectId(Long projectId) {
            this.projectId = projectId;
            return this;
        }

        public RequestLogBuilder method(String method) {
            this.method = method;
            return this;
        }

        public RequestLogBuilder path(String path) {
            this.path = path;
            return this;
        }

        public RequestLogBuilder requestTime(LocalDateTime requestTime) {
            this.requestTime = requestTime;
            return this;
        }

        public RequestLogBuilder statusCode(Integer statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public RequestLogBuilder responseTime(Long responseTime) {
            this.responseTime = responseTime;
            return this;
        }

        public RequestLogBuilder requestIp(String requestIp) {
            this.requestIp = requestIp;
            return this;
        }

        public RequestLogBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public RequestLogBuilder requestHeaders(String requestHeaders) {
            this.requestHeaders = requestHeaders;
            return this;
        }

        public RequestLogBuilder queryParams(String queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        public RequestLogBuilder requestBody(String requestBody) {
            this.requestBody = requestBody;
            return this;
        }

        public RequestLogBuilder responseBody(String responseBody) {
            this.responseBody = responseBody;
            return this;
        }

        public RequestLogBuilder responseContentType(String responseContentType) {
            this.responseContentType = responseContentType;
            return this;
        }

        public RequestLog build() {
            RequestLog log = new RequestLog();
            log.setMockApiId(mockApiId);
            log.setProjectId(projectId);
            log.setMethod(method);
            log.setPath(path);
            log.setRequestTime(requestTime);
            log.setStatusCode(statusCode);
            log.setResponseTime(responseTime);
            log.setRequestIp(requestIp);
            log.setUserId(userId);
            log.setRequestHeaders(requestHeaders);
            log.setQueryParams(queryParams);
            log.setRequestBody(requestBody);
            log.setResponseBody(responseBody);
            log.setResponseContentType(responseContentType);
            return log;
        }
    }
}
