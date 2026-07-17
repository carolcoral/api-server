/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto;

import com.carolcoral.apiserver.entity.ResponseRequestParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 响应请求参数DTO
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Schema(description = "响应请求参数DTO")
public class ResponseRequestParamDTO {

    @Schema(description = "参数ID")
    private Long id;

    @Schema(description = "参数名称", example = "userId", required = true)
    @NotBlank(message = "参数名称不能为空")
    private String paramName;

    @Schema(description = "参数类型", example = "REQUEST_BODY", required = true)
    private String paramType = "REQUEST_BODY";

    @Schema(description = "参数值", example = "123")
    private String paramValue;

    @Schema(description = "是否必填", example = "true")
    private Boolean required = true;

    /**
     * 获取参数ID
     *
     * @return 参数ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置参数ID
     *
     * @param id 参数ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取参数名称
     *
     * @return 参数名称
     */
    public String getParamName() {
        return paramName;
    }

    /**
     * 设置参数名称
     *
     * @param paramName 参数名称
     */
    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    /**
     * 获取参数类型
     *
     * @return 参数类型
     */
    public String getParamType() {
        return paramType;
    }

    /**
     * 设置参数类型
     *
     * @param paramType 参数类型
     */
    public void setParamType(String paramType) {
        this.paramType = paramType;
    }

    /**
     * 获取参数值
     *
     * @return 参数值
     */
    public String getParamValue() {
        return paramValue;
    }

    /**
     * 设置参数值
     *
     * @param paramValue 参数值
     */
    public void setParamValue(String paramValue) {
        this.paramValue = paramValue;
    }

    /**
     * 获取是否必填
     *
     * @return 是否必填
     */
    public Boolean getRequired() {
        return required;
    }

    /**
     * 设置是否必填
     *
     * @param required 是否必填
     */
    public void setRequired(Boolean required) {
        this.required = required;
    }
}
