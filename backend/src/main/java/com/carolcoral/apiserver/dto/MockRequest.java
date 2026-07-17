/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Mock 请求数据传输对象
 * <p>
 * 封装一次 Mock 请求的完整信息，包括路径、方法、请求头、参数、请求体等。
 * 在自定义响应处理器（{@code CustomResponseTransformer}）中，
 * 可以通过此对象获取请求上下文，实现基于请求内容的动态响应。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 根据请求参数做条件判断
 * String username = (String) mockRequest.getParams().get("username");
 * if ("admin".equals(username)) {
 *     // 返回管理员专属响应
 * }
 *
 * // 获取 RESTful 路径参数
 * String userId = mockRequest.getPathParams().get("id");
 * }</pre>
 *
 * @author carolcoral
 * @see com.carolcoral.apiserver.plugin.CustomResponseTransformer
 */
@Schema(description = "Mock请求")
public class MockRequest {

    /**
     * 请求路径
     * <p>完整的 URL 路径，如 {@code /api/user/login}、{@code /products/123}。</p>
     */
    @Schema(description = "请求路径", example = "/api/user/login")
    private String path;

    /**
     * 请求方法
     * <p>HTTP 方法名，如 {@code GET}、{@code POST}、{@code PUT}、{@code DELETE}。</p>
     */
    @Schema(description = "请求方法", example = "POST")
    private String method;

    /**
     * 请求头键值对
     * <p>包含所有 HTTP 请求头，如 {@code Content-Type}、{@code Authorization}、{@code User-Agent} 等。</p>
     */
    @Schema(description = "请求头")
    private Map<String, String> headers;

    /**
     * URL 查询参数键值对
     * <p>URL 中 {@code ?} 后面的参数，如 {@code /api/list?page=1&size=10} 对应
     * {@code {page: "1", size: "10"}}。</p>
     */
    @Schema(description = "请求参数")
    private Map<String, Object> params;

    /**
     * 请求体
     * <p>POST/PUT 请求的 body 内容，可能是 JSON 字符串、Map 对象等。</p>
     */
    @Schema(description = "请求体")
    private Object body;

    /**
     * 项目编码
     * <p>标识请求所属的 Mock 项目，用于多项目隔离场景。</p>
     */
    @Schema(description = "项目编码", example = "ecmall")
    private String projectCode;

    /**
     * RESTful 路径参数键值对
     * <p>路径中的动态参数，如 {@code /user/{id}} 匹配 {@code /user/42} 时，
     * 对应 {@code {id: "42"}}。</p>
     */
    @Schema(description = "路径参数（RESTful风格）")
    private Map<String, String> pathParams;

    // Getters and Setters
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public Map<String, String> getPathParams() {
        return pathParams;
    }

    public void setPathParams(Map<String, String> pathParams) {
        this.pathParams = pathParams;
    }
}
