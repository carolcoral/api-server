/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 响应请求参数实体类
 * 用于管理响应匹配的请求参数
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Schema(description = "响应请求参数实体")
@Entity
@Table(name = "t_response_request_param")
public class ResponseRequestParam {

    @Schema(description = "参数ID", example = "1")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "参数名称", example = "userId")
    @Column(nullable = false, length = 100)
    private String paramName;

    @Schema(description = "参数类型", example = "REQUEST_BODY")
    @Column(nullable = false, length = 50)
    private ParamType paramType = ParamType.REQUEST_BODY;

    @Schema(description = "参数值", example = "123")
    @Column(columnDefinition = "TEXT")
    private String paramValue;

    @Schema(description = "是否必填", example = "true")
    @Column(nullable = false)
    private Boolean required = true;

    @Schema(description = "创建时间")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @Column(nullable = false)
    private LocalDateTime updateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_id", nullable = false)
    @Schema(description = "所属响应")
    private MockResponse mockResponse;

    /**
     * 参数类型枚举
     */
    public enum ParamType {
        PATH,
        QUERY,
        REQUEST_BODY,
        HEADER,
        FILE
    }

    /**
     * 无参构造函数
     */
    public ResponseRequestParam() {
    }

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
    public ParamType getParamType() {
        return paramType;
    }

    /**
     * 设置参数类型
     *
     * @param paramType 参数类型
     */
    public void setParamType(ParamType paramType) {
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

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间
     *
     * @param createTime 创建时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置更新时间
     *
     * @param updateTime 更新时间
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * 获取所属响应
     *
     * @return 所属响应
     */
    public MockResponse getMockResponse() {
        return mockResponse;
    }

    /**
     * 设置所属响应
     *
     * @param mockResponse 所属响应
     */
    public void setMockResponse(MockResponse mockResponse) {
        this.mockResponse = mockResponse;
    }

    /**
     * 判断对象是否相等（基于ID）
     *
     * @param o 要比较的对象
     * @return 如果ID相同返回true，否则返回false
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResponseRequestParam that = (ResponseRequestParam) o;
        return Objects.equals(id, that.id);
    }

    /**
     * 计算哈希码（基于ID）
     *
     * @return 哈希码
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * 返回对象的字符串表示
     *
     * @return 对象的字符串表示
     */
    @Override
    public String toString() {
        return "ResponseRequestParam{" +
                "id=" + id +
                ", paramName='" + paramName + '\'' +
                ", paramType=" + paramType +
                ", paramValue='" + paramValue + '\'' +
                ", required=" + required +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", mockResponse=" + mockResponse +
                '}';
    }

    /**
     * 持久化前回调方法
     * 设置创建时间和更新时间
     */
    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    /**
     * 更新前回调方法
     * 设置更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
