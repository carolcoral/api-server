/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 代理请求录制实体类
 * 用于记录录制回放页面通过代理发起的真实HTTP请求
 *
 * @author carolcoral
 */
@Schema(description = "代理请求录制实体")
@Entity
@Table(name = "t_proxy_record", indexes = {
    @Index(name = "idx_pr_request_time", columnList = "request_time")
})
public class ProxyRecord {

    @Schema(description = "记录ID", example = "1")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "请求方法", example = "GET")
    @Column(nullable = false, length = 10, name = "method")
    private String method;

    @Schema(description = "请求路径", example = "/api/users")
    @Column(nullable = false, length = 500, name = "path")
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
}
