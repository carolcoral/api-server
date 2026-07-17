/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Mock 响应数据传输对象
 * <p>
 * 封装一次 Mock 请求的响应数据，包括状态码、响应头、响应体和延迟设置。
 * 在自定义响应处理器（{@code CustomResponseTransformer}）中，
 * 通过此对象读取原始响应信息并构建新的响应。
 * </p>
 *
 * <h3>Builder 模式示例</h3>
 * <pre>{@code
 * MockResponseDTO response = MockResponseDTO.builder()
 *         .statusCode(200)
 *         .headers(originalHeaders)
 *         .body(customBody)
 *         .delay(100)
 *         .build();
 * }</pre>
 *
 * @author carolcoral
 * @see com.carolcoral.apiserver.plugin.CustomResponseTransformer
 */
@Schema(description = "Mock响应")
public class MockResponseDTO {

    /**
     * HTTP 状态码
     * <p>标准 HTTP 状态码，如 200（成功）、404（未找到）、500（服务器错误）等。</p>
     */
    @Schema(description = "HTTP状态码", example = "200")
    private Integer statusCode;

    /**
     * 响应头键值对
     * <p>如 {@code Content-Type: application/json}、{@code X-Custom-Header: value} 等。</p>
     */
    @Schema(description = "响应头")
    private Map<String, String> headers;

    /**
     * 响应体
     * <p>可以是 JSON 字符串、{@link Map} 对象、{@link java.util.List} 对象等任意类型。
     * 在自定义处理器中通过 {@code getBody()} 获取后进行转换处理。</p>
     */
    @Schema(description = "响应体")
    private Object body;

    /**
     * 响应延迟时间（毫秒）
     * <p>模拟网络延迟，{@code null} 或 0 表示无延迟。在构建新响应时通常保留原值。</p>
     */
    @Schema(description = "响应延迟（毫秒）", example = "100")
    private Integer delay;

    /**
     * 默认构造器
     */
    public MockResponseDTO() {
    }

    /**
     * 全参构造器
     *
     * @param statusCode HTTP 状态码
     * @param headers    响应头键值对
     * @param body       响应体
     * @param delay      响应延迟（毫秒）
     */
    public MockResponseDTO(Integer statusCode, Map<String, String> headers, Object body, Integer delay) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
        this.delay = delay;
    }

    // Getters and Setters
    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public Integer getDelay() {
        return delay;
    }

    public void setDelay(Integer delay) {
        this.delay = delay;
    }

    /**
     * 创建 Builder 实例
     * <p>推荐使用 Builder 模式构建 MockResponseDTO 对象，链式调用更清晰。</p>
     *
     * @return 新的 {@link MockResponseDTOBuilder} 实例
     */
    public static MockResponseDTOBuilder builder() {
        return new MockResponseDTOBuilder();
    }

    /**
     * MockResponseDTO 的 Builder 类
     * <p>提供流式 API 构建 MockResponseDTO 对象，支持链式调用。</p>
     */
    public static class MockResponseDTOBuilder {
        private Integer statusCode;
        private Map<String, String> headers;
        private Object body;
        private Integer delay;

        /**
         * 设置 HTTP 状态码
         *
         * @param statusCode HTTP 状态码
         * @return 当前 Builder 实例，支持链式调用
         */
        public MockResponseDTOBuilder statusCode(Integer statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        /**
         * 设置响应头
         *
         * @param headers 响应头键值对
         * @return 当前 Builder 实例，支持链式调用
         */
        public MockResponseDTOBuilder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        /**
         * 设置响应体
         *
         * @param body 响应体内容，可以是 String、Map、List 等
         * @return 当前 Builder 实例，支持链式调用
         */
        public MockResponseDTOBuilder body(Object body) {
            this.body = body;
            return this;
        }

        /**
         * 设置响应延迟
         *
         * @param delay 延迟时间（毫秒）
         * @return 当前 Builder 实例，支持链式调用
         */
        public MockResponseDTOBuilder delay(Integer delay) {
            this.delay = delay;
            return this;
        }

        /**
         * 构建 MockResponseDTO 实例
         *
         * @return 新的 {@link MockResponseDTO} 对象
         */
        public MockResponseDTO build() {
            return new MockResponseDTO(statusCode, headers, body, delay);
        }
    }
}
