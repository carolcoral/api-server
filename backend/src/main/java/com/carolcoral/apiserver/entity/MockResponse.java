/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * 接口响应实体类
 * 用于管理API接口模拟系统中的响应信息
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-02
 */
@Schema(description = "接口响应实体")
@Entity
@Table(name = "t_mock_response")
public class MockResponse {

    @Schema(description = "响应ID", example = "1")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "HTTP状态码", example = "200")
    @Column(nullable = false)
    private Integer statusCode;

    @Schema(description = "响应内容类型", example = "application/json")
    @Column(nullable = false, length = 100)
    private String contentType = "application/json";

    @Schema(description = "响应头（JSON格式）", example = "{\"X-Custom-Header\": \"value\"}")
    @Column(columnDefinition = "TEXT")
    private String headers;

    @Schema(description = "响应体", example = "{\"code\": 200, \"message\": \"success\"}")
    @Column(columnDefinition = "TEXT", nullable = false)
    private String responseBody;

    @Schema(description = "权重（用于随机返回）", example = "50")
    @Column
    private Integer weight = 100;

    @Schema(description = "条件表达式", example = "$.userId == '123'")
    @Column(length = 500)
    private String condition;

    @Schema(description = "条件描述", example = "当userId等于123时返回此响应")
    @Column(length = 200)
    private String conditionDesc;

    @Schema(description = "是否启用", example = "true")
    @Column(nullable = false)
    private Boolean enabled = true;

    @Schema(description = "是否为当前激活的响应", example = "false")
    @Column(nullable = false)
    private Boolean active = false;

    @Schema(description = "是否为默认响应", example = "true")
    @Column(nullable = false)
    private Boolean isDefault = false;

    @Schema(description = "响应耗时（毫秒）", example = "100")
    @Column
    private Integer responseDelay = 0;

    @Schema(description = "创建时间")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @Column(nullable = false)
    private LocalDateTime updateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_id", nullable = false)
    @Schema(description = "所属接口")
    private MockApi mockApi;

    /**
     * 无参构造函数
     */
    public MockResponse() {
    }

    /**
     * 全参构造函数
     *
     * @param id 响应ID
     * @param statusCode HTTP状态码
     * @param contentType 响应内容类型
     * @param headers 响应头
     * @param responseBody 响应体
     * @param weight 权重
     * @param condition 条件表达式
     * @param conditionDesc 条件描述
     * @param enabled 是否启用
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @param mockApi 所属接口
     */
    public MockResponse(Long id, Integer statusCode, String contentType, String headers,
                        String responseBody, Integer weight, String condition,
                        String conditionDesc, Boolean enabled, LocalDateTime createTime,
                        LocalDateTime updateTime, MockApi mockApi) {
        this.id = id;
        this.statusCode = statusCode;
        this.contentType = contentType;
        this.headers = headers;
        this.responseBody = responseBody;
        this.weight = weight;
        this.condition = condition;
        this.conditionDesc = conditionDesc;
        this.enabled = enabled;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.mockApi = mockApi;
    }

    /**
     * 获取响应ID
     *
     * @return 响应ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置响应ID
     *
     * @param id 响应ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取HTTP状态码
     *
     * @return HTTP状态码
     */
    public Integer getStatusCode() {
        return statusCode;
    }

    /**
     * 设置HTTP状态码
     *
     * @param statusCode HTTP状态码
     */
    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    /**
     * 获取响应内容类型
     *
     * @return 响应内容类型
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * 设置响应内容类型
     *
     * @param contentType 响应内容类型
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * 获取响应头
     *
     * @return 响应头
     */
    public String getHeaders() {
        return headers;
    }

    /**
     * 设置响应头
     *
     * @param headers 响应头
     */
    public void setHeaders(String headers) {
        this.headers = headers;
    }

    /**
     * 获取响应体
     *
     * @return 响应体
     */
    public String getResponseBody() {
        return responseBody;
    }

    /**
     * 设置响应体
     *
     * @param responseBody 响应体
     */
    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    /**
     * 获取权重
     *
     * @return 权重
     */
    public Integer getWeight() {
        return weight;
    }

    /**
     * 设置权重
     *
     * @param weight 权重
     */
    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    /**
     * 获取条件表达式
     *
     * @return 条件表达式
     */
    public String getCondition() {
        return condition;
    }

    /**
     * 设置条件表达式
     *
     * @param condition 条件表达式
     */
    public void setCondition(String condition) {
        this.condition = condition;
    }

    /**
     * 获取条件描述
     *
     * @return 条件描述
     */
    public String getConditionDesc() {
        return conditionDesc;
    }

    /**
     * 设置条件描述
     *
     * @param conditionDesc 条件描述
     */
    public void setConditionDesc(String conditionDesc) {
        this.conditionDesc = conditionDesc;
    }

    /**
     * 获取是否启用
     *
     * @return 是否启用
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用
     *
     * @param enabled 是否启用
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取是否为当前激活的响应
     *
     * @return 是否激活
     */
    public Boolean getActive() {
        return active;
    }

    /**
     * 设置是否为当前激活的响应
     *
     * @param active 是否激活
     */
    public void setActive(Boolean active) {
        this.active = active;
    }

    /**
     * 获取是否为默认响应
     *
     * @return 是否为默认响应
     */
    public Boolean getIsDefault() {
        return isDefault;
    }

    /**
     * 设置是否为默认响应
     *
     * @param isDefault 是否为默认响应
     */
    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    /**
     * 获取响应耗时
     *
     * @return 响应耗时（毫秒）
     */
    public Integer getResponseDelay() {
        return responseDelay;
    }

    /**
     * 设置响应耗时
     *
     * @param responseDelay 响应耗时（毫秒）
     */
    public void setResponseDelay(Integer responseDelay) {
        this.responseDelay = responseDelay;
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
     * 获取所属接口
     *
     * @return 所属接口
     */
    @JsonIgnore
    public MockApi getMockApi() {
        return mockApi;
    }

    /**
     * 设置所属接口
     *
     * @param mockApi 所属接口
     */
    public void setMockApi(MockApi mockApi) {
        this.mockApi = mockApi;
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
        MockResponse that = (MockResponse) o;
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
        return "MockResponse{" +
                "id=" + id +
                ", statusCode=" + statusCode +
                ", contentType='" + contentType + '\'' +
                ", headers='" + headers + '\'' +
                ", responseBody='" + responseBody + '\'' +
                ", weight=" + weight +
                ", condition='" + condition + '\'' +
                ", conditionDesc='" + conditionDesc + '\'' +
                ", enabled=" + enabled +
                ", active=" + active +
                ", isDefault=" + isDefault +
                ", responseDelay=" + responseDelay +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", mockApi=" + mockApi +
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
