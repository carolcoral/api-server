/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 通用API响应DTO
 *
 * @author carolcoral
 */
@Schema(description = "通用API响应")
public class ApiResponse<T> {

    @Schema(description = "响应码", example = "200")
    private Integer code;

    @Schema(description = "响应消息", example = "操作成功")
    private String message;

    @Schema(description = "响应数据")
    private T data;

    @Schema(description = "时间戳", example = "1703123456789")
    private Long timestamp;

    /**
     * 默认构造器
     */
    public ApiResponse() {
    }

    /**
     * Builder方法
     */
    public static <T> ApiResponseBuilder<T> builder() {
        return new ApiResponseBuilder<>();
    }

    /**
     * Builder类
     */
    public static class ApiResponseBuilder<T> {
        private Integer code;
        private String message;
        private T data;
        private Long timestamp;

        public ApiResponseBuilder<T> code(Integer code) {
            this.code = code;
            return this;
        }

        public ApiResponseBuilder<T> message(String message) {
            this.message = message;
            return this;
        }

        public ApiResponseBuilder<T> data(T data) {
            this.data = data;
            return this;
        }

        public ApiResponseBuilder<T> timestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ApiResponse<T> build() {
            ApiResponse<T> response = new ApiResponse<>();
            response.code = code;
            response.message = message;
            response.data = data;
            response.timestamp = timestamp;
            return response;
        }
    }

    /**
     * 成功响应
     *
     * @param data 数据
     * @param <T>  数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("操作成功")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 成功响应（无数据）
     *
     * @param <T> 数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    /**
     * 错误响应
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> error(String message) {
        return error(500, message);
    }

    /**
     * 错误响应（自定义状态码）
     *
     * @param code    错误码
     * @param message 错误消息
     * @param <T>     数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 未授权响应
     *
     * @param <T> 数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> unauthorized() {
        return error(401, "未授权访问");
    }

    /**
     * 禁止访问响应
     *
     * @param <T> 数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> forbidden() {
        return error(403, "禁止访问");
    }

    /**
     * 未找到响应
     *
     * @param <T> 数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> notFound() {
        return error(404, "资源未找到");
    }

    // Getters and Setters
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
